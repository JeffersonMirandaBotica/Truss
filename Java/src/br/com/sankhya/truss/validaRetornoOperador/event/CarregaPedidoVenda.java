package br.com.sankhya.truss.validaRetornoOperador.event;

import br.com.sankhya.extensions.eventoprogramavel.EventoProgramavelJava;
import br.com.sankhya.jape.event.PersistenceEvent;
import br.com.sankhya.jape.event.TransactionContext;
import br.com.sankhya.jape.vo.DynamicVO;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CarregaPedidoVenda implements EventoProgramavelJava {


    @Override
    public void beforeInsert(PersistenceEvent event) throws Exception {
        preenchePedido(event);
    }

    @Override
    public void beforeUpdate(PersistenceEvent event) throws Exception {

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



    private static void preenchePedido(PersistenceEvent evt) throws Exception {
        DynamicVO cabVO = (DynamicVO) evt.getVo();
        BigDecimal nunotaPedido = extrairNrPedidoCliente(cabVO.asString("OBSERVACAO"));
        cabVO.setProperty("AD_OBSERVNOTA", nunotaPedido);
    }

    public static BigDecimal extrairNrPedidoCliente(String texto) {
        if (texto == null || texto.isEmpty()) return null;

        String regex = "Nr\\. Pedido Cliente:\\s*(\\d+)";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(texto);

        if (matcher.find()) {
            String numeroStr = matcher.group(1);
            return new BigDecimal(numeroStr);
        }

        return null;
    }


}
