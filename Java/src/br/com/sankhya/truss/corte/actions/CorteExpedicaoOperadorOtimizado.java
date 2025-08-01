package br.com.sankhya.truss.corte.actions;

import br.com.sankhya.jape.EntityFacade;
import br.com.sankhya.jape.bmp.PersistentLocalEntity;
import br.com.sankhya.jape.dao.JdbcWrapper;
import br.com.sankhya.jape.sql.NativeSql;
import br.com.sankhya.jape.vo.DynamicVO;
import br.com.sankhya.jape.vo.EntityVO;
import br.com.sankhya.jape.wrapper.JapeFactory;
import br.com.sankhya.jape.wrapper.JapeWrapper;
import br.com.sankhya.modelcore.auth.AuthenticationInfo;
import br.com.sankhya.modelcore.comercial.CentralItemNota;
import br.com.sankhya.modelcore.comercial.centrais.CACHelper;
import br.com.sankhya.modelcore.comercial.impostos.ImpostosHelpper;
import br.com.sankhya.modelcore.util.DynamicEntityNames;
import br.com.sankhya.modelcore.util.EntityFacadeFactory;
import br.com.sankhya.truss.corte.helper.CorteHelper;
import br.com.sankhya.ws.ServiceContext;
import com.sankhya.util.TimeUtils;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class CorteExpedicaoOperadorOtimizado {
    private static BigDecimal shelflife = null;
    public void executaCorte(BigDecimal nunota) throws Exception {
        try {
            JapeWrapper iteDAO = JapeFactory.dao(DynamicEntityNames.ITEM_NOTA);
            JapeWrapper cabDAO = JapeFactory.dao(DynamicEntityNames.CABECALHO_NOTA);
            JapeWrapper proDAO = JapeFactory.dao(DynamicEntityNames.PRODUTO);
            JapeWrapper parDAO = JapeFactory.dao(DynamicEntityNames.PARCEIRO);
            JapeWrapper prefDAO = JapeFactory.dao(DynamicEntityNames.PARAMETRO_SISTEMA);
            EntityFacade dwfEntityFacade = EntityFacadeFactory.getDWFFacade();
            JdbcWrapper jdbc = dwfEntityFacade.getJdbcWrapper();

            ServiceContext sctx = new ServiceContext(null);
            sctx.setAutentication(AuthenticationInfo.getCurrent());
            sctx.makeCurrent();

            DynamicVO cabVO = cabDAO.findByPK(nunota);
            DynamicVO parVO = parDAO.findByPK(cabVO.asBigDecimal("CODPARC"));

            if (!cabVO.asString("AD_STATUSPED").equals("4")) {
                throw new Exception("O Pedido deve estar com status Pedido Aprovado.");
            }

            Collection<DynamicVO> itesVO = iteDAO.find("NUNOTA = ?", nunota);
            int qtdItens = itesVO.size();
            int countDel = 0;

            BigDecimal adcShelfLife = prefDAO.findOne("CHAVE = ?", "DIAADCSHELFLIFE").asBigDecimalOrZero("INTEIRO");
            BigDecimal shelflife = parVO.asBigDecimal("AD_SHELFLIFE");

            if(shelflife == null){
                throw new Exception("<b>Parceiro não tem Shelf Life cadastrado. Procure a área responsável</b>");
            } else {
                shelflife = shelflife.add(adcShelfLife);
            }

            this.shelflife = shelflife;

            NativeSql s = new NativeSql(jdbc);
            s.setNamedParameter("P_NUNOTA", nunota);
            ResultSet result = s.executeQuery(" SELECT " +
                    " PRE.NUNOTA, " +
                    " PRE.SEQUENCIA, " +
                    " PRE.CODLOCAL, " +
                    " PRE.CODPROD, " +
                    " PRE.DISPONIVELTERCEIRO, " +
                    " PRE.QTDNEG, " +
                    " PRE.QTDPEDIDO " +
                    " FROM AD_VW_PREVIEWPEDIDO PRE " +
                    " JOIN TGFITE ITE ON ITE.NUNOTA = PRE.NUNOTA AND ITE.SEQUENCIA = PRE.SEQUENCIA AND ITE.CODLOCALORIG = PRE.CODLOCAL " +
                    " WHERE PRE.NUNOTA = :P_NUNOTA " +
                    " ORDER BY PRE.SEQUENCIA ");


            while (result.next()) {

                BigDecimal sequencia = result.getBigDecimal("SEQUENCIA");
                BigDecimal qtdpedido = result.getBigDecimal("QTDPEDIDO");
                BigDecimal qtdneg = result.getBigDecimal("QTDNEG");
                DynamicVO iteVO = iteDAO.findByPK(nunota, sequencia);

                if(qtdpedido.equals(BigDecimal.ZERO)) {
                    countDel++;
                    iteDAO.prepareToUpdateByPK(nunota, sequencia)
                            .set("AD_CLASSCORT", "RT")
                            .update();
                    iteDAO.delete(new Object[]{nunota, sequencia});

                } else if (qtdpedido.compareTo(qtdneg) < 0){
                    iteDAO.prepareToUpdateByPK(nunota, sequencia)
                            .set("AD_CLASSCORT", "RP")
                            .update();

                    iteVO.setProperty("QTDNEG", qtdpedido);
                    iteVO.setProperty("VLRTOT", qtdpedido.multiply(iteVO.asBigDecimal("VLRUNIT")));

                    CentralItemNota itemNota = new CentralItemNota();
                    itemNota.recalcularValores("QTDNEG", qtdpedido.toString(), iteVO, nunota);


                    List<DynamicVO> itensVO = new ArrayList<DynamicVO>();
                    itensVO.add(iteVO);

                    CACHelper cacHelper = new CACHelper();
                    cacHelper.incluirAlterarItem(nunota, sctx, null, true, itensVO);

                    iteVO.setProperty("AD_CLASSCORT", null);
                    dwfEntityFacade.saveEntity(DynamicEntityNames.ITEM_NOTA, (EntityVO) iteVO);

                }


            }

            // Atualiza a TOP de Corte
            BigDecimal topCorte = CorteHelper.buscaTopCorte(cabVO, jdbc);
            Timestamp dhTopCorte = null;
            NativeSql sql = new NativeSql(jdbc);
            sql.setNamedParameter("P_CODTIPOPER", topCorte);
            ResultSet rs = sql.executeQuery("SELECT MAX(DHALTER) AS DHALTER FROM TGFTOP WHERE CODTIPOPER = :P_CODTIPOPER");
            if (rs.next()) {
                dhTopCorte = rs.getTimestamp("DHALTER");
            }

            cabDAO.prepareToUpdate(cabVO)
                    .set("CODTIPOPER", topCorte)
                    .set("DHTIPOPER", dhTopCorte)
                    .update();


            // Atualiza itens para reservar
            NativeSql q = new NativeSql(jdbc);
            q.setNamedParameter("P_NUNOTA", nunota);
            ResultSet r = q.executeQuery("SELECT NUNOTA, SEQUENCIA FROM TGFITE WHERE NUNOTA = :P_NUNOTA");

            while (r.next()) {
                iteDAO.prepareToUpdateByPK(r.getBigDecimal("NUNOTA"), r.getBigDecimal("SEQUENCIA"))
                        .set("ATUALESTOQUE", BigDecimal.ONE)
                        .set("RESERVA", "S")
                        .update();
            }


            if (countDel == qtdItens) {
                cabDAO.prepareToUpdateByPK(nunota)
                        .set("AD_CLASSCORTE", "RT")
                        .update();

                cabDAO.deleteByCriteria("NUNOTA = ?", nunota);
            } else {

                indicaLotes(nunota);
                cabDAO.prepareToUpdate(cabVO)
                        .set("AD_DTLIBEXP", TimeUtils.getNow())
                        .set("AD_STATUSPED", "28")
                        .update();

            }

        } catch (Exception e) {
            e.printStackTrace();
            throw new Exception(e.getMessage());
        }
    }



    private void indicaLotes(BigDecimal nunota) throws Exception {
        JapeWrapper iteDAO = JapeFactory.dao(DynamicEntityNames.ITEM_NOTA);
        JapeWrapper proDAO = JapeFactory.dao(DynamicEntityNames.PRODUTO);
        JapeWrapper cabDAO = JapeFactory.dao(DynamicEntityNames.CABECALHO_NOTA);
        Collection<DynamicVO> itesVO = iteDAO.find("NUNOTA = ?", nunota);
        EntityFacade dwfEntityFacade = EntityFacadeFactory.getDWFFacade();
        JdbcWrapper jdbc = dwfEntityFacade.getJdbcWrapper();
        DynamicVO cabVO = cabDAO.findByPK(nunota);

        try {

            cabVO.setProperty("AD_DESCONSCORTE", "S");
            dwfEntityFacade.saveEntity(DynamicEntityNames.CABECALHO_NOTA, (EntityVO) cabVO);

            NativeSql q = new NativeSql(jdbc);
            q.setNamedParameter("P_NUNOTA", nunota);
            ResultSet r = q.executeQuery("SELECT V.*, ROW_NUMBER() OVER (PARTITION BY SEQUENCIA ORDER BY SEQUENCIA) AS LINHA FROM AD_VW_PEDIDOPORLOTE2 V WHERE NUNOTA = :P_NUNOTA");

            while(r.next()) {
                BigDecimal linha = r.getBigDecimal("LINHA");
                String controle = r.getString("CONTROLE");
                BigDecimal quantidade = r.getBigDecimal("QTD_A_SEPARAR");
                DynamicVO iteVO = iteDAO.findByPK(nunota, r.getBigDecimal("SEQUENCIA"));
                BigDecimal codparcest = r.getBigDecimal("CODPARC");

                if(linha.equals(BigDecimal.ONE)){
                    iteVO.setProperty("CONTROLE", controle);
                    iteVO.setProperty("QTDNEG", quantidade);
                    iteVO.setProperty("VLRTOT", quantidade.multiply(iteVO.asBigDecimal("VLRUNIT")));
                    iteVO.setProperty("CONTROLE", controle);
                    iteVO.setProperty("AD_CLASSCORT", "L");
                    iteVO.setProperty("AD_CODPARCEST", codparcest);
                    dwfEntityFacade.saveEntity(DynamicEntityNames.ITEM_NOTA, (EntityVO) iteVO);
                    iteVO.setProperty("AD_CLASSCORT", null);
                    dwfEntityFacade.saveEntity(DynamicEntityNames.ITEM_NOTA, (EntityVO) iteVO);
                } else {
                    insereItem(iteVO, quantidade, controle, codparcest);
                }
            }
            recalculaNota(nunota);
        } catch(Exception e){
            e.printStackTrace();
            throw new Exception("Erro ao indicar Lotes: " + e.getMessage());
        } finally {
            jdbc.closeSession();
        }

    }

    private static void insereItem (DynamicVO iteVO, BigDecimal quantidade, String controle, BigDecimal codparcEst) throws Exception {

        JapeWrapper iteDAO = JapeFactory.dao(DynamicEntityNames.ITEM_NOTA);
        try {

            iteDAO.create()
                    .set("NUNOTA", iteVO.asBigDecimal("NUNOTA"))
                    .set("CODPROD", iteVO.asBigDecimal("CODPROD"))
                    .set("QTDNEG", quantidade)
                    .set("CONTROLE", controle)
                    .set("VLRUNIT", iteVO.asBigDecimal("VLRUNIT"))
                    .set("VLRTOT", iteVO.asBigDecimal("VLRUNIT").multiply(quantidade))
                    .set("CODLOCALORIG", iteVO.asBigDecimal("CODLOCALORIG"))
                    .set("CODVOL", iteVO.asString("CODVOL"))
                    .set("ATUALESTOQUE", iteVO.asBigDecimal("ATUALESTOQUE"))
                    .set("RESERVA", iteVO.asString("RESERVA"))
                    .set("NUTAB", iteVO.asBigDecimal("NUTAB"))
                    .set("AD_CODPARCEST", codparcEst)
                    .save();
        } catch(Exception e) {
            throw new Exception("Erro ao incluir itens de lote: " + e.getMessage());
        }



    }


    public static void recalculaNota(BigDecimal nunota) throws Exception {
        JapeWrapper cabDAO = JapeFactory.dao(DynamicEntityNames.CABECALHO_NOTA);
        DynamicVO cabVO = cabDAO.findByPK(nunota);

        EntityFacade dwfEntityFacade = EntityFacadeFactory.getDWFFacade();
        PersistentLocalEntity persistentEntityCab = dwfEntityFacade.findEntityByPrimaryKey(DynamicEntityNames.CABECALHO_NOTA, new Object[] { nunota});

        ImpostosHelpper imposto = new ImpostosHelpper();
        imposto.carregarNota(nunota);
        imposto.calculaICMS(true);

        imposto.totalizarNota(nunota);
        imposto.setForcarRecalculo(true);
        imposto.setAtualizaImpostos(true);
        imposto.setCalcularTudo(true);
        imposto.calcularImpostos(nunota);
        imposto.salvarNota();

        BigDecimal totalNota = imposto.calcularTotalNota(cabVO.asBigDecimal("NUNOTA"), imposto.calcularTotalItens(cabVO.asBigDecimal("NUNOTA"), false));

        cabVO.setProperty("VLRNOTA", totalNota);
        persistentEntityCab.setValueObject((EntityVO)cabVO);
    }


}


