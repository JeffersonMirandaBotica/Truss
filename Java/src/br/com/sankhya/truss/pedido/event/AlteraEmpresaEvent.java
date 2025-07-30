package br.com.sankhya.truss.pedido.event;

import br.com.sankhya.extensions.eventoprogramavel.EventoProgramavelJava;
import br.com.sankhya.jape.event.PersistenceEvent;
import br.com.sankhya.jape.event.TransactionContext;
import br.com.sankhya.jape.vo.DynamicVO;
import br.com.sankhya.jape.wrapper.JapeFactory;
import br.com.sankhya.jape.wrapper.JapeWrapper;
import br.com.sankhya.modelcore.util.DynamicEntityNames;

import java.math.BigDecimal;

public class AlteraEmpresaEvent implements EventoProgramavelJava {
    @Override
    public void beforeInsert(PersistenceEvent event) throws Exception {
        alteraEmpresa(event,"I");
    }

    @Override
    public void beforeUpdate(PersistenceEvent event) throws Exception {
        alteraEmpresa(event,"U");
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


    private static void alteraEmpresa(PersistenceEvent evt, String tipo) throws Exception {
        DynamicVO cabVO = (DynamicVO) evt.getVo();

        JapeWrapper parDAO = JapeFactory.dao(DynamicEntityNames.PARCEIRO);

        try{
            if (!cabVO.asBigDecimal("CODEMP").equals(BigDecimal.ONE) && !cabVO.asBigDecimal("CODEMP").equals(BigDecimal.valueOf(6))){
                return;
            }

            if(!(cabVO.asString("TIPMOV").equals("P") )){
                return;
            }

            if( cabVO.asBigDecimal("CODTIPOPER").equals(BigDecimal.valueOf(3102))) {
                return;
            }

            if("U".equals(tipo) ){
                DynamicVO oldCabVO = (DynamicVO) evt.getOldVO();
                if(oldCabVO.asBigDecimal("CODPARC").equals(cabVO.asBigDecimal("CODPARC")) && oldCabVO.asBigDecimal("CODEMP").equals(cabVO.asBigDecimal("CODEMP"))){
                    return;
                }
            }

            DynamicVO parVO = parDAO.findByPK(cabVO.asBigDecimal("CODPARC"));
            String localSeparacao = parVO.asString("AD_LOCALSEPARACAO");

            if(localSeparacao == null) {
                throw new Exception("Parceiro nao possui local de separacao especificado. Verifique o cadastro.");
            }

            if(localSeparacao.equals("2")) {
                cabVO.setProperty("CODEMP", BigDecimal.valueOf(6));
            } else {
                cabVO.setProperty("CODEMP", BigDecimal.ONE);
            }

        } catch(Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Erro no evento Altera Empresa: " + e.getMessage());
        }

    }
}
