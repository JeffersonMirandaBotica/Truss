package br.com.sankhya.truss.enviaSeparacao.actions;

import br.com.sankhya.extensions.actionbutton.AcaoRotinaJava;
import br.com.sankhya.extensions.actionbutton.ContextoAcao;
import br.com.sankhya.extensions.actionbutton.Registro;
import br.com.sankhya.jape.EntityFacade;
import br.com.sankhya.jape.dao.JdbcWrapper;
import br.com.sankhya.jape.sql.NativeSql;
import br.com.sankhya.jape.wrapper.JapeFactory;
import br.com.sankhya.jape.wrapper.JapeWrapper;
import br.com.sankhya.modelcore.util.DynamicEntityNames;
import br.com.sankhya.modelcore.util.EntityFacadeFactory;
import com.sankhya.util.TimeUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;


public class EnviaSeparacaoXls implements AcaoRotinaJava {
    @Override
    public void doAction(ContextoAcao ctx) throws Exception {
        Boolean erro = false;
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Envio Separacao");

        // Geração do nome do arquivo com data e hora atual
        String dataFormatada = new SimpleDateFormat("dd_MM_yyyy_HHmmss").format(new Date());
        String nomeArquivo = "EnvioSeparacao_" + dataFormatada + ".csv";

        // Inicialização das facades e wrappers necessários
        EntityFacade dwfEntityFacade = EntityFacadeFactory.getDWFFacade();
        JdbcWrapper jdbc = dwfEntityFacade.getJdbcWrapper();

        JapeWrapper histDAO = JapeFactory.dao("AD_HISTENVIOSEP"); // Tabela de histórico
        JapeWrapper cabDAO = JapeFactory.dao(DynamicEntityNames.CABECALHO_NOTA); // TGFCAB

        // Cabeçalho
        Row headerRow = sheet.createRow(0);
        String[] colunas = {"CdMaterial", "Descrição", "QtdeCaixa", "QtdeUnid", "lote", "NrPedido", "Tipo", "Nfe", "Transportador"};
        for (int i = 0; i < colunas.length; i++) {
            headerRow.createCell(i).setCellValue(colunas[i]);
        }

        // Índice da linha
        int rowIdx = 1;

        try {
            // Recupera os registros selecionados na tela
            Registro[] linhas = ctx.getLinhas();
            String errmsg = "Os seguintes produtos devem ter os lotes preenchidos: <br>";
            for (Registro linha : linhas) {
                // Obtém o NUNOTA de cada registro selecionado
                BigDecimal nunota = (BigDecimal) linha.getCampo("NUNOTA");

                // Consulta os itens do pedido (TGFCAB + TGFITE + TGFPRO)
                String sql = "SELECT " +
                        " ITE.CODPROD, " +
                        " PRO.DESCRPROD, " +
                        " CEIL((ITE.QTDNEG - ITE.QTDENTREGUE) / PRO.QTDEMB) AS QTDCAIXA, " +
                        " (ITE.QTDNEG - ITE.QTDENTREGUE) AS QTDNEG, " +
                        " ITE.CONTROLE, " +
                        " SEP.NUNOTA, " +
                        "CASE " + 
                        " WHEN PAI.CODPAIS = 55 THEN 'NACIONAL'" +
                        " ELSE 'INTER' " +
                        " END AS TIPO," +
                        " CASE" +
                        " WHEN PRO.TIPCONTEST = 'L' AND ITE.CONTROLE = ' ' THEN ITE.CODPROD " +
                        " ELSE 0 " +
                        " END AS CODPRODSEMLOTE " +
                        " FROM TGFCAB SEP " +
                        " JOIN TGFITE ITE ON ITE.NUNOTA = SEP.NUNOTA " +
                        " JOIN TGFPRO PRO ON PRO.CODPROD = ITE.CODPROD " +
                        " JOIN TGFPAR PAR ON PAR.CODPARC = SEP.CODPARC " +
                        " JOIN TSICID CID ON CID.CODCID = PAR.CODCID " +
                        " JOIN TSIUFS UFS ON UFS.CODUF = CID.UF " +
                        " JOIN TSIPAI PAI ON PAI.CODPAIS = UFS.CODPAIS " +
                        " WHERE SEP.NUNOTA = :P_NUNOTA ";

                NativeSql query = new NativeSql(jdbc);
                query.setNamedParameter("P_NUNOTA", nunota);
                ResultSet r = query.executeQuery(sql);





                // Percorre os resultados da consulta
                while (r.next()) {
                    // Extrai os dados do ResultSet
                    String codprod = r.getBigDecimal("CODPROD").toString();
                    String descrprod = r.getString("DESCRPROD");
                    String qtdcaixa = r.getBigDecimal("QTDCAIXA").toString();
                    String qtdneg = r.getBigDecimal("QTDNEG").toString();
                    String controle = r.getString("CONTROLE");
                    String pedido = r.getBigDecimal("NUNOTA").toString();
                    String tipo = r.getString("TIPO").toString();
                    BigDecimal codprodsemlote = r.getBigDecimal("CODPRODSEMLOTE");
                    if(!codprodsemlote.equals(BigDecimal.ZERO)){
                        errmsg = errmsg + codprodsemlote + " <br>";
                        erro = true;
                    }
                    // Dentro do loop while (r.next())
                    Row row = sheet.createRow(rowIdx++);
                    row.createCell(0).setCellValue(codprod);
                    row.createCell(1).setCellValue(descrprod);
                    row.createCell(2).setCellValue(Double.parseDouble(qtdcaixa));
                    row.createCell(3).setCellValue(Double.parseDouble(qtdneg));
                    row.createCell(4).setCellValue(controle);
                    row.createCell(5).setCellValue(pedido);
                    row.createCell(6).setCellValue(tipo);
                    row.createCell(7).setCellValue(""); // NFE
                    row.createCell(8).setCellValue(""); // Transportador

                    // Registra o envio na tabela de histórico
                    histDAO.create()
                            .set("NUNOTA", nunota)
                            .set("CODPROD", new BigDecimal(codprod))
                            .set("DESCRPROD", descrprod)
                            .set("QTDCAIXA", new BigDecimal(qtdcaixa))
                            .set("QTDNEG", new BigDecimal(qtdneg))
                            .set("CONTROLE", controle)
                            .set("DHENVIO", TimeUtils.getNow()) // Data/Hora atual
                            .set("CODUSU", ctx.getUsuarioLogado()) // Usuário que executou a ação
                            .save();

                    // Atualiza o cabeçalho da nota com data de liberação e status do pedido
                    cabDAO.prepareToUpdateByPK(nunota)
                            .set("AD_DTLIBEXP", TimeUtils.getNow()) // Campo customizado
                            .set("AD_STATUSPED", "29") // Status customizado da separação
                            .update();
                }

            }

            if (erro) {
                throw new RuntimeException(errmsg);
            }

            // Após preencher todas as linhas
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out); // importante: escrever conteúdo
            out.flush();         // importante: forçar escrita

            // Encode Base64 do XLSX
            String base64xlsx = Base64.getEncoder().encodeToString(out.toByteArray());
            String base64Url = "data:application/vnd.openxmlformats-officedocument.spreadsheetml.sheet;base64," + base64xlsx;
            nomeArquivo = "EnvioSeparacao_" + dataFormatada + ".xlsx";



            String html = "<html>" +
                    "<body>" +
                    "<p>Arquivo gerado com sucesso.</p>" +
                    "<p>Se o download não começar automaticamente, <a id='downloadLink' href='" + base64Url + "' download='" + nomeArquivo + "'>clique aqui</a>.</p>" +
                    "<script>document.getElementById('downloadLink').click();</script>" +
                    "</body>" +
                    "</html>";


            String link = "<a download='" + nomeArquivo + "' " +
                    "href='data:application/vnd.openxmlformats-officedocument.spreadsheetml.sheet;base64," +
                    base64xlsx + "'>Clique aqui para baixar o arquivo</a>";




            ctx.setMensagemRetorno(html);
        } catch (Exception e) {
            // Em caso de erro, exibe a exceção
            e.printStackTrace();
            ctx.mostraErro(e.getMessage());
        }
    }
}





