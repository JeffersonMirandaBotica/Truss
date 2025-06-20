package br.com.sankhya.truss.corte.regras;

import br.com.sankhya.extensions.regrasnegocio.ContextoRegra;
import br.com.sankhya.extensions.regrasnegocio.RegraNegocioJava;
import br.com.sankhya.jape.vo.DynamicVO;
import br.com.sankhya.jape.wrapper.JapeFactory;
import br.com.sankhya.jape.wrapper.JapeWrapper;
import br.com.sankhya.modelcore.util.DynamicEntityNames;
import br.com.sankhya.truss.corte.helper.CorteHelper;

import java.math.BigDecimal;

public class RegraConfirmacaoNota implements RegraNegocioJava {

    @Override
    public void executa(ContextoRegra ctx) throws Exception {

        BigDecimal nunota = ctx.getNunota();
        JapeWrapper parDAO = JapeFactory.dao(DynamicEntityNames.PARCEIRO);
        JapeWrapper cabDAO = JapeFactory.dao(DynamicEntityNames.CABECALHO_NOTA);
        DynamicVO cabVO = cabDAO.findByPK(nunota);


        try {

            DynamicVO parVO = parDAO.findByPK(cabVO.asBigDecimal("CODPARC"));
            BigDecimal codparc = parVO.asBigDecimal("CODPARC");
            String separaTerceiros = parVO.asString("AD_LOCALSEPARACAO");
            separaTerceiros = separaTerceiros == null ? "1" : separaTerceiros;

            if ("2".equals(separaTerceiros)) {
                boolean exigeLote = CorteHelper.parcExigeLote(codparc).equals("S");
                boolean temControlePreenchido = temControlePreenchido(nunota);
                boolean temControleNaoPreenchido = temControleNaoPreenchido(nunota);

                if(!exigeLote && temControlePreenchido) {
                    throw new Exception("O país do parceiro exige que o lote seja preenchido manualmente.");
                } else if(exigeLote && temControleNaoPreenchido) {
                    throw new Exception("Para esta operação o lote não pode ser preenchido.");
                }

                if(!exigeLote) {
                    CorteHelper.indicaLotes(nunota);
                }

                cabDAO.prepareToUpdateByPK(nunota)
                        .set("AD_STATUSPED", "28")
                        .update();

            }

            ctx.setSucesso(true);

        } catch(Exception e){
            e.printStackTrace();
            ctx.setSucesso(false);
            ctx.setMensagem("Erro na regra de negócio\n" + e.getMessage());
            ctx.setCodUsuLib(0);
            throw new Exception("Erro na regra de negócio\n" + e.getMessage());
        }

    }

    private static boolean temControlePreenchido(BigDecimal nunota) throws Exception {
        JapeWrapper iteDAO = JapeFactory.dao(DynamicEntityNames.ITEM_NOTA);

        DynamicVO iteVO = iteDAO.findOne("NUNOTA = ? AND CONTROLE <> ' '", nunota);

        if (iteVO != null) {
            return true;
        } else {
            return false;
        }

    }

    private static boolean temControleNaoPreenchido(BigDecimal nunota) throws Exception {
        JapeWrapper iteDAO = JapeFactory.dao(DynamicEntityNames.ITEM_NOTA);

        DynamicVO iteVO = iteDAO.findOne("NUNOTA = ? AND CONTROLE = ' '", nunota);

        if (iteVO != null) {
            return true;
        } else {
            return false;
        }

    }

}



