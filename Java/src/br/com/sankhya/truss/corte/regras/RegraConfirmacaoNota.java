package br.com.sankhya.truss.corte.regras;

import br.com.sankhya.extensions.regrasnegocio.ContextoRegra;
import br.com.sankhya.extensions.regrasnegocio.RegraNegocioJava;
import br.com.sankhya.jape.dao.JdbcWrapper;
import br.com.sankhya.jape.sql.NativeSql;
import br.com.sankhya.jape.vo.DynamicVO;
import br.com.sankhya.jape.wrapper.JapeFactory;
import br.com.sankhya.jape.wrapper.JapeWrapper;
import br.com.sankhya.modelcore.util.DynamicEntityNames;
import br.com.sankhya.truss.corte.helper.CorteHelper;

import java.math.BigDecimal;
import java.sql.ResultSet;

public class RegraConfirmacaoNota implements RegraNegocioJava {

    @Override
    public void executa(ContextoRegra ctx) throws Exception {

        BigDecimal nunota = ctx.getNunota();
        JapeWrapper parDAO = JapeFactory.dao(DynamicEntityNames.PARCEIRO);
        JapeWrapper cabDAO = JapeFactory.dao(DynamicEntityNames.CABECALHO_NOTA);
        JapeWrapper prefDAO = JapeFactory.dao(DynamicEntityNames.PARAMETRO_SISTEMA);
        DynamicVO cabVO = cabDAO.findByPK(nunota);


        try {

            DynamicVO parVO = parDAO.findByPK(cabVO.asBigDecimal("CODPARC"));
            BigDecimal codparc = parVO.asBigDecimal("CODPARC");
            String separaTerceiros = parVO.asString("AD_LOCALSEPARACAO");
            separaTerceiros = separaTerceiros == null ? "1" : separaTerceiros;
            String tipmov = cabVO.asString("TIPMOV");

            if ("2".equals(separaTerceiros) || ("J".equals(tipmov) && codparc.equals(BigDecimal.valueOf(6)))) {
                boolean exigeLote = CorteHelper.parcExigeLote(codparc).equals("S");
                boolean temControlePreenchido = temControlePreenchido(nunota);
                boolean temControleNaoPreenchido = temControleNaoPreenchido(nunota);

                if(exigeLote && temControleNaoPreenchido) {
                    throw new Exception("O país do parceiro exige lote específico. Faça o preenchimento do lote dos produtos acabados.");
                }



                    CorteHelper.indicaLotes(nunota);

                    if(cabVO.asString("STATUSNOTA").equals("L")) {
                        cabDAO.prepareToUpdateByPK(nunota)
                                .set("AD_STATUSPED", "28")
                                .update();
                    }


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
        JdbcWrapper jdbc = JapeFactory.getEntityFacade().getJdbcWrapper();
        NativeSql query = new NativeSql(jdbc);

        query.setNamedParameter("P_NUNOTA", nunota);
        ResultSet r = query.executeQuery("SELECT COUNT(1) AS QTD " +
                " FROM TGFITE ITE " +
                " JOIN TGFPRO PRO ON PRO.CODPROD = ITE.CODPROD " +
                " WHERE ITE.NUNOTA = :P_NUNOTA " +
                " AND ITE.CONTROLE <> ' ' " +
                " AND PRO.CODGRUPOPROD LIKE '52%' ");

        BigDecimal qtd = BigDecimal.ZERO;
        while(r.next()) {
            qtd = r.getBigDecimal("QTD");
        }


        if (qtd.compareTo(BigDecimal.ZERO) > 0) {
            return true;
        } else {
            return false;
        }

    }

    private static boolean temControleNaoPreenchido(BigDecimal nunota) throws Exception {
        JdbcWrapper jdbc = JapeFactory.getEntityFacade().getJdbcWrapper();
        NativeSql query = new NativeSql(jdbc);

        query.setNamedParameter("P_NUNOTA", nunota);
        ResultSet r = query.executeQuery("SELECT COUNT(1) AS QTD " +
                " FROM TGFITE ITE " +
                " JOIN TGFPRO PRO ON PRO.CODPROD = ITE.CODPROD " +
                " WHERE ITE.NUNOTA = :P_NUNOTA " +
                " AND ITE.CONTROLE = ' ' " +
                " AND PRO.CODGRUPOPROD LIKE '52%' ");

        BigDecimal qtd = BigDecimal.ZERO;
        while(r.next()) {
            qtd = r.getBigDecimal("QTD");
        }


        if (qtd.compareTo(BigDecimal.ZERO) > 0) {
            return true;
        } else {
            return false;
        }

    }

}



