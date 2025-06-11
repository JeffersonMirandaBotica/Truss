package br.com.sankhya.truss.validaRetornoOperador.event;

import br.com.sankhya.extensions.eventoprogramavel.EventoProgramavelJava;
import br.com.sankhya.jape.EntityFacade;
import br.com.sankhya.jape.dao.JdbcWrapper;
import br.com.sankhya.jape.event.PersistenceEvent;
import br.com.sankhya.jape.event.TransactionContext;
import br.com.sankhya.jape.sql.NativeSql;
import br.com.sankhya.jape.vo.DynamicVO;
import br.com.sankhya.jape.wrapper.JapeFactory;
import br.com.sankhya.jape.wrapper.JapeWrapper;
import br.com.sankhya.modelcore.util.DynamicEntityNames;
import br.com.sankhya.modelcore.util.EntityFacadeFactory;

import java.math.BigDecimal;
import java.sql.ResultSet;

public class LiberaRetornoMenor implements EventoProgramavelJava {
    @Override
    public void beforeInsert(PersistenceEvent event) throws Exception {

    }

    @Override
    public void beforeUpdate(PersistenceEvent event) throws Exception {
        liberaRetorno(event);
    }

    @Override
    public void beforeDelete(PersistenceEvent event) throws Exception {

    }

    @Override
    public void afterInsert(PersistenceEvent event) throws Exception {

    }

    @Override
    public void afterUpdate(PersistenceEvent event) throws Exception {

    }

    @Override
    public void afterDelete(PersistenceEvent event) throws Exception {

    }

    @Override
    public void beforeCommit(TransactionContext tranCtx) throws Exception {

    }

    private static void liberaRetorno (PersistenceEvent evt) throws Exception {
        JapeWrapper iteDAO = JapeFactory.dao(DynamicEntityNames.ITEM_NOTA);
        JapeWrapper cabDAO = JapeFactory.dao(DynamicEntityNames.CABECALHO_NOTA);
        EntityFacade dwfEntityFacade = EntityFacadeFactory.getDWFFacade();
        JdbcWrapper jdbc = dwfEntityFacade.getJdbcWrapper();


        try {
            DynamicVO libVO = (DynamicVO) evt.getVo();
            BigDecimal evento = libVO.asBigDecimal("EVENTO");
            String status = "7";

            if (!evento.equals(BigDecimal.valueOf(1009))) {
                return;
            }

            DynamicVO oldLibVO = (DynamicVO) evt.getOldVO();


            if (libVO.asTimestamp("DHLIB") != null && oldLibVO.asTimestamp("DHLIB") == null && !"S".equals(libVO.asString("REPROVADO"))) {
                BigDecimal nunota = libVO.asBigDecimal("NUCHAVE");
                BigDecimal nunotaPedido = null;
                NativeSql query = new NativeSql(jdbc);
                query.setNamedParameter("P_NUNOTA", nunota);
                ResultSet r = query.executeQuery("SELECT V.*, PRO.DESCRPROD " +
                        " FROM AD_VW_VALIDARETORNO V " +
                        " JOIN TGFPRO PRO ON PRO.CODPROD = V.CODPROD " +
                        " WHERE NUNOTA_REFERENCIA = :P_NUNOTA " +
                        " AND COMPARACAO_QTD IN ('MENOR')");

                while(r.next()) {
                    nunotaPedido = r.getBigDecimal("NUNOTA_PEDIDO");
                    BigDecimal codprod = r.getBigDecimal("CODPROD");
                    String controle = r.getString("CONTROLE");
                    DynamicVO iteVO = iteDAO.findOne("NUNOTA = ? AND CODPROD = ? AND CONTROLE = ?", nunotaPedido, codprod, controle);
                    BigDecimal qtdConferida = r.getBigDecimal("QTD_RELACIONADA").subtract(r.getBigDecimal("QTD_ORIGEM")).add(iteVO.asBigDecimal("QTDENTREGUE"));

                    if(qtdConferida.compareTo(BigDecimal.ZERO) > 0) {
                        status = "30";
                    }

                    if(iteVO != null) {
                        iteDAO.prepareToUpdate(iteVO)
                                .set("QTDCONFERIDA", qtdConferida)
                                .update();
                    }
                }

                cabDAO.prepareToUpdateByPK(nunotaPedido)
                        .set("AD_STATUSPED", status)
                        .update();
            }

        } catch(Exception e) {
            e.printStackTrace();
            throw new Exception("Falha no evento Libera Retorno Menor: " + e.getMessage());
        } finally {
            jdbc.closeSession();
        }





    }

}
