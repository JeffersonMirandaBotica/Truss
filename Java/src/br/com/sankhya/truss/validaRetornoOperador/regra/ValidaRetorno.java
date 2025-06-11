package br.com.sankhya.truss.validaRetornoOperador.regra;

import br.com.sankhya.extensions.regrasnegocio.ContextoRegra;
import br.com.sankhya.extensions.regrasnegocio.RegraNegocioJava;
import br.com.sankhya.jape.EntityFacade;
import br.com.sankhya.jape.dao.JdbcWrapper;
import br.com.sankhya.jape.sql.NativeSql;
import br.com.sankhya.jape.vo.DynamicVO;
import br.com.sankhya.jape.wrapper.JapeFactory;
import br.com.sankhya.jape.wrapper.JapeWrapper;
import br.com.sankhya.modelcore.util.DynamicEntityNames;
import br.com.sankhya.modelcore.util.EntityFacadeFactory;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.Collection;

public class ValidaRetorno implements RegraNegocioJava {
    @Override
    public void executa(ContextoRegra ctx) throws Exception {
        BigDecimal nunota = ctx.getNunota();
        EntityFacade dwfEntityFacade = EntityFacadeFactory.getDWFFacade();
        JdbcWrapper jdbc = dwfEntityFacade.getJdbcWrapper();
        JapeWrapper cabDAO = JapeFactory.dao(DynamicEntityNames.CABECALHO_NOTA);
        JapeWrapper iteDAO = JapeFactory.dao(DynamicEntityNames.ITEM_NOTA);
        String msgError = "Não foi possível confirmar a nota. Itens do retorno divergente do pedido.<br><b>Itens Divergentes:</b><br>";

        try{
            jdbc.openSession();
            DynamicVO cabVO = cabDAO.findByPK(nunota);

            BigDecimal nunotaPedido = cabVO.asBigDecimal("AD_OBSERVNOTA");

            if(nunotaPedido == null) {
                throw new Exception("Nota de retorno deve ter o pedido vinculado.");
            }

            DynamicVO pedidoVO = cabDAO.findByPK(nunotaPedido);

            if(pedidoVO == null) {
                throw new Exception("Pedido de venda informado não é válido.");
            }

            if(!pedidoVO.asString("TIPMOV").equals("P")){
                throw new Exception("Pedido de venda informado não é válido.");
            }

            // Primeira parte: valida se existem itens não existentes ou excedentes no arquivo de retorno

            NativeSql query = new NativeSql(jdbc);
            query.setNamedParameter("P_NUNOTA", nunota);
            ResultSet r = query.executeQuery("SELECT V.*, PRO.DESCRPROD " +
                    " FROM AD_VW_VALIDARETORNO V " +
                    " JOIN TGFPRO PRO ON PRO.CODPROD = V.CODPROD " +
                    " WHERE NUNOTA_REFERENCIA = :P_NUNOTA " +
                    " AND COMPARACAO_QTD IN ('NAO_EXISTE', 'MAIOR')");
            int count = 0;

            while(r.next()) {
                msgError = msgError + count + ". <b>Produto: </b>" + r.getBigDecimal("CODPROD") + " " + r.getString("DESCRPROD") +
                        " <b> Controle: </b> " + r.getString("CONTROLE") +
                        " | <b>Qtd. Retorno:</b> " + r.getBigDecimal("QTD_ORIGEM") +
                        " | <b>Qtd. Pedido:</b> " + r.getBigDecimal("QTD_RELACIONADA") +
                        "<br>";
                count++;
            }

            if(count > 0){
                throw new Exception(msgError);
            }

            // Segunda parte: valida se retorno possui quantidades menores que o pedido

            msgError = "Retorno do Operador com quantidade menor que o pedido. \n Itens Divergentes:\n";

            NativeSql query2 = new NativeSql(jdbc);
            query2.setNamedParameter("P_NUNOTA", nunota);
            ResultSet r2 = query2.executeQuery("SELECT V.*, PRO.DESCRPROD " +
                    " FROM AD_VW_VALIDARETORNO V " +
                    " JOIN TGFPRO PRO ON PRO.CODPROD = V.CODPROD " +
                    " WHERE NUNOTA_REFERENCIA = :P_NUNOTA " +
                    " AND COMPARACAO_QTD IN ('MENOR')");


            while(r2.next()) {
                count++;
                msgError = msgError + count + ". Produto: " + r2.getBigDecimal("CODPROD") + " " + r2.getString("DESCRPROD") +
                        "  Controle:  " + r2.getString("CONTROLE") +
                        " | Qtd. Retorno: " + r2.getBigDecimal("QTD_ORIGEM") +
                        " | Qtd. Pedido: " + r2.getBigDecimal("QTD_RELACIONADA") + "\n";
            }

            if(count > 0) {
                ctx.setSucesso(false);
                ctx.setMensagem(msgError);
                ctx.setCodUsuLib(0);
            } else {
                // Atualiza status para liberado para faturamento
                cabDAO.prepareToUpdateByPK(nunotaPedido)
                        .set("AD_STATUSPED", "7")
                        .update();

                // Atualiza quantidade de corte dos itens para zero
                Collection<DynamicVO> itesVO = iteDAO.find("NUNOTA = ?", nunotaPedido);
                for(DynamicVO iteVO : itesVO){
                    iteDAO.prepareToUpdate(iteVO)
                            .set("QTDCONFERIDA", BigDecimal.ZERO)
                            .update();
                }

                ctx.setSucesso(true);
                ctx.setMensagem("");
                ctx.setCodUsuLib(0);
            }


        } catch(Exception e) {
            e.printStackTrace();
            throw new Exception("Erro na regra valida retorno: " + e.getMessage());
        } finally {
            jdbc.closeSession();
        }
    }
}