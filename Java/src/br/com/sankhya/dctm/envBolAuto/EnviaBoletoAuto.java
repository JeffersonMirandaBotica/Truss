package br.com.sankhya.dctm.envBolAuto;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.cuckoo.core.ScheduledAction;
import org.cuckoo.core.ScheduledActionContext;

import com.sankhya.util.BigDecimalUtil;
import com.sankhya.util.TimeUtils;

import br.com.sankhya.jape.EntityFacade;
import br.com.sankhya.jape.bmp.PersistentLocalEntity;
import br.com.sankhya.jape.core.JapeSession.SessionHandle;
import br.com.sankhya.jape.dao.JdbcWrapper;
import br.com.sankhya.jape.sql.NativeSql;
import br.com.sankhya.jape.util.JapeSessionContext;
import br.com.sankhya.jape.vo.DynamicVO;
import br.com.sankhya.jape.vo.EntityVO;
import br.com.sankhya.jape.wrapper.JapeFactory;
import br.com.sankhya.jape.wrapper.JapeWrapper;
import br.com.sankhya.modelcore.auth.AuthenticationInfo;
import br.com.sankhya.modelcore.comercial.BoletoHelper;
import br.com.sankhya.modelcore.comercial.ImpressaoNotaHelpper;
import br.com.sankhya.modelcore.util.ArquivoModeloUtils;
import br.com.sankhya.modelcore.util.DynamicEntityNames;
import br.com.sankhya.modelcore.util.EntityFacadeFactory;
import br.com.sankhya.modelcore.util.MGECoreParameter;
import br.com.sankhya.modelcore.util.Report;
import br.com.sankhya.modelcore.util.ReportManager;
import br.com.sankhya.util.ConcatenatePDF;
import br.com.sankhya.ws.ServiceContext;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperPrint;

public class EnviaBoletoAuto implements ScheduledAction {

	ConcatenatePDF arquivos = new ConcatenatePDF();
	Boolean erro = false;
	String nomeAnexo = "";
	EnviaEmailAutoHelper helper = new EnviaEmailAutoHelper();

	BigDecimal codAnexo = null;

	@Override
	public void onTime(ScheduledActionContext arg0) {
		try {
			System.out.println("Inicio EnviaBoletoAuto ");
			BuscaFinanceiros();

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void BuscaFinanceiros() {
		EntityFacade entityFacade = EntityFacadeFactory.getDWFFacade();
		JdbcWrapper jdbc = entityFacade.getJdbcWrapper();
		NativeSql nativeSql = null;

		erro = false;

		try {

			int codModMail = MGECoreParameter.getParameterAsInt("IDMODMAILBOLAUT");
			BigDecimal codModMailBig = new BigDecimal(codModMail);

			nativeSql = new NativeSql(jdbc);
			nativeSql.appendSql(" SELECT FIN.NUFIN, FIN.NUNOTA, FIN.CODPARC ");
			nativeSql.appendSql(" FROM TGFFIN FIN ");
			nativeSql.appendSql(" WHERE NVL(FIN.AD_BOLETOREGISTRADO,'N') = 'S' ");
			nativeSql.appendSql(" AND NVL(FIN.AD_BOLETOIMPRESSO,'N') = 'N' ");
			nativeSql.appendSql(" AND FIN.ORIGEM = 'E' ");
			nativeSql.appendSql(" AND FIN.NUFIN = 655550 ");

			ResultSet resultado = nativeSql.executeQuery();
			while (resultado.next()) {

				BigDecimal nufin = resultado.getBigDecimal("NUFIN");
				BigDecimal codParc = resultado.getBigDecimal("CODPARC");
				BigDecimal nuNota = resultado.getBigDecimal("NUNOTA");

				System.out.println("nufin: " + nufin);
				enviaBoleto(nufin, nuNota, codParc, codModMailBig);

			}
		} catch (Exception e) {
			e.printStackTrace();
		}finally {
			jdbc.closeSession();
		}
	}

	private void enviaBoleto(BigDecimal nufin, BigDecimal nuNota, BigDecimal codParc, BigDecimal codModMail)
			throws Exception {

		System.out.println("Entrou enviaBoleto");

		BigDecimal codFila = null;
		
		@SuppressWarnings("unused")
		SessionHandle hnd = null;
		String email = "tales.alves@sankhya.com.br";
		EntityFacade dwfEntityFacade = EntityFacadeFactory.getDWFFacade();

		PersistentLocalEntity persistent = dwfEntityFacade.findEntityByPrimaryKey(DynamicEntityNames.PARCEIRO, codParc);
		DynamicVO parceiroVO = (DynamicVO) persistent.getValueObject();

		String emailParc = parceiroVO.asString("EMAILNFE");
		System.out.println("emailParc: " + emailParc);

		String[] listaEmails = emailParc.split(";");

		for (String emailSeparado : listaEmails) {
			System.out.println(emailSeparado.trim());
		}

		final PersistentLocalEntity persistentModBol = dwfEntityFacade.findEntityByPrimaryKey("ModeloEmail",
				codModMail);
		final DynamicVO modBolVO = (DynamicVO) persistentModBol.getValueObject();

		String assunto = modBolVO.asString("ASSUNTO");
		String corpoemail = modBolVO.asString("CONTEUDO");
		BigDecimal codSMTP = modBolVO.asBigDecimal("CODSMTP");

		char[] mensagem = corpoemail.toCharArray();

		try {

			codFila = helper.insereEmail(mensagem, assunto, email, codSMTP);

			System.out.println("nro fila: " + codFila);

			try {

				byte[] arquivo = impressaoNota(nuNota);

				helper.insereAnexo(nomeAnexo, arquivo, codFila);

				//byte[] arquivoBoleto = buscarBoleto(nuNota);

				//helper.insereAnexo("Boleto", arquivoBoleto, codFila);

				System.out.println("nro ANEXO: " + codAnexo);

			} catch (Exception e) {
				erro = true;
				helper.insereLogTransf(e.getMessage(), nufin, nuNota);
				e.printStackTrace();
			} finally {
				// JapeSession.close(anexo);
			}

		} catch (Exception e) {
			erro = true;
			helper.insereLogTransf(e.getMessage(), nufin, nuNota);
			e.printStackTrace();
		}

		if (erro && codFila.intValue() > 0) {
			try {
				helper.deletaFila(codFila);
			} catch (Exception e) {
				helper.insereLogTransf(e.getMessage(), nufin, nuNota);
				e.printStackTrace();
			}
		}

		if (!erro) {

			try {

				PersistentLocalEntity persistentFin = dwfEntityFacade
						.findEntityByPrimaryKey(DynamicEntityNames.FINANCEIRO, nufin);
				DynamicVO financeiroVO = (DynamicVO) persistentFin.getValueObject();

				financeiroVO.setProperty("AD_BOLETOIMPRESSO", "S");
				financeiroVO.setProperty("AD_DTBOLETOIMPRESSO", new Timestamp(System.currentTimeMillis()));
				persistentFin.setValueObject((EntityVO) financeiroVO);

			} catch (Exception e) {
				helper.insereLogTransf(e.getMessage(), nufin, nuNota);
				e.printStackTrace();
			}
		}

	}

	protected void registry(BigDecimal codUsu) throws Exception {
		EntityFacade dwfEntityFacade = null;
		dwfEntityFacade = EntityFacadeFactory.getDWFFacade();

		if (AuthenticationInfo.getCurrentOrNull() != null) {
			AuthenticationInfo.unregistry();
		}

		DynamicVO usuarioVO = (DynamicVO) dwfEntityFacade.findEntityByPrimaryKeyAsVO("Usuario",
				new Object[] { BigDecimalUtil.ZERO_VALUE });
		StringBuffer authID = new StringBuffer();
		authID.append(System.currentTimeMillis()).append(':').append(usuarioVO.asBigDecimal("CODUSU")).append(':')
				.append(hashCode());
		AuthenticationInfo authInfo = new AuthenticationInfo(usuarioVO.asString("NOMEUSU"),
				usuarioVO.asBigDecimalOrZero("CODUSU"), usuarioVO.asBigDecimalOrZero("CODGRUPO"),
				new Integer(authID.toString().hashCode()));
		authInfo.makeCurrent();
		ServiceContext sctx = new ServiceContext(null);
		sctx.setAutentication(authInfo);
		sctx.makeCurrent();
		JapeSessionContext.putProperty("usuario_logado", authInfo.getUserID());
		JapeSessionContext.putProperty("emp_usu_logado", usuarioVO.asBigDecimal("CODEMP"));
		JapeSessionContext.putProperty("dh_atual", new Timestamp(System.currentTimeMillis()));
		JapeSessionContext.putProperty("d_atual", new Timestamp(TimeUtils.getToday()));
		JapeSessionContext.putProperty("usuarioVO", usuarioVO);
		JapeSessionContext.putProperty("authInfo", authInfo);
	}

	

	@SuppressWarnings("static-access")
	private byte[] impressaoNota(BigDecimal nuNota) {

		System.out.println("Entrou impressaoNota");

		nomeAnexo = "";

		EntityFacade dwfEntityFacade = EntityFacadeFactory.getDWFFacade();

		JdbcWrapper jdbc = dwfEntityFacade.getJdbcWrapper();

		byte[] arqFatura = null;
		try {

			jdbc.openSession();

			BigDecimal nroRelatorio = new BigDecimal(0);
			Map<String, Object> parameters = new HashMap<>();
			Report modeloImpressao = null;

			NativeSql nativeSql = null;

			nativeSql = new NativeSql(jdbc);
			nativeSql.appendSql(" SELECT CAB.NUNOTA, CAB.NUMNOTA, TP.CODMODDOC, ");
			nativeSql.appendSql(" NVL(CAB.STATUSNFSE,'N') AS STATUSNFSE, ");
			nativeSql.appendSql(" NVL(CAB.STATUSNFE,'N') AS STATUSNFE, ");
			nativeSql
					.appendSql(" (SELECT NURFE FROM TGFMON WHERE CODMODNF = NVL(NVL(NUM.MODNOTAFIS,TP.CODMODNFSE),TP.CODMODNF)) NURFE ");
			nativeSql.appendSql(" FROM TGFCAB CAB ");
			nativeSql.appendSql(" JOIN TGFTOP TP ON CAB.CODTIPOPER = TP.CODTIPOPER AND CAB.DHTIPOPER = TP.DHALTER ");
			nativeSql.appendSql(" LEFT JOIN TGFNUM NUM ");
			nativeSql.appendSql(
					" ON ARQUIVO = 'VENDA' AND CAB.SERIENOTA = NUM.SERIE AND CAB.CODEMP = NUM.CODEMP AND TP.CODMODDOC = NUM.CODMODDOC ");
			nativeSql.appendSql(" WHERE CAB.NUNOTA = " + nuNota);

			ResultSet resultado = nativeSql.executeQuery();
			while (resultado.next()) {

				String statusNFSe = resultado.getString("STATUSNFSE");
				String statusNFe = resultado.getString("STATUSNFE");

				String codModDoc = resultado.getString("CODMODDOC");
				BigDecimal numNota = resultado.getBigDecimal("NUMNOTA");

				System.out.println("nuNota: " + nuNota);
				System.out.println("statusNFSe: " + statusNFSe);
				System.out.println("statusNFe: " + statusNFe);
				System.out.println("codModDoc: " + codModDoc);

				JapeWrapper notaDAO = JapeFactory.dao("CabecalhoNota");
				Collection<DynamicVO> notasVO = notaDAO.find("NUNOTA = ?", nuNota);
				for (@SuppressWarnings("unused")
				DynamicVO notaVO : notasVO) {

					BigDecimal numeroNota = (BigDecimal) notaVO.getProperty("NUNOTA");

					// inicio gera��o do relat�rio fatura
					nroRelatorio = resultado.getBigDecimal("NURFE");
					parameters = new HashMap<>();

					@SuppressWarnings("unused")
					ImpressaoNotaHelpper impressaoNotaHelpper = new ImpressaoNotaHelpper();

					parameters.put("NUNOTA", numeroNota);
					parameters.put("PDIR_MODELO", ArquivoModeloUtils.getDiretorioModelos());

					modeloImpressao = ReportManager.getInstance().getReport(nroRelatorio, dwfEntityFacade);

					JasperPrint jasperPrint = null;

					jasperPrint = modeloImpressao.buildJasperPrint(parameters, jdbc.getConnection());

					arqFatura = JasperExportManager.exportReportToPdf(jasperPrint);

					// arquivos.addPdfFile(arqFatura);
					if (statusNFe.equals("A")){
						nomeAnexo = "NFe_" + numNota;
						
					}else {

					nomeAnexo = "NFSe_" + numNota;
					}
					
					// arqFatura = null;
				}

			}

		} catch (

		Exception e) {
			erro = true;
			jdbc.closeSession();
			e.printStackTrace();
		} finally {
			jdbc.closeSession();
		}
		return arqFatura;
	}

	public byte[] buscarBoleto(BigDecimal nunota) throws Exception {

		System.out.println("Entrou buscarBoleto");

		byte[] boleto = null;
		ConcatenatePDF pdfList = new ConcatenatePDF();
		pdfList.setNumeration(false);

		// BOLETO

		JdbcWrapper JDBC = JapeFactory.getEntityFacade().getJdbcWrapper();
		NativeSql nativeSql = new NativeSql(JDBC);

		StringBuilder sql = new StringBuilder();

		sql.append(" SELECT FIN.NUFIN, FIN.CODCTABCOINT, FIN.CODBCO, FIN.CODEMP ");
		sql.append("   FROM TGFFIN FIN, TGFTIT TIT ");
		sql.append("  WHERE FIN.CODTIPTIT = TIT.CODTIPTIT ");
		sql.append("    AND NVL(TIT.PROIBIMPBOL,'N') = 'N' ");
		sql.append("    AND FIN.NUNOTA = " + nunota);
		/*
		 * sql.append("    UNION ALL ");
		 * sql.append(" SELECT FIN.NUFIN, FIN.CODCTABCOINT, FIN.CODBCO, FIN.CODEMP ");
		 * sql.append("   FROM TGFFIN FIN, TGFTIT TIT ");
		 * sql.append("  WHERE FIN.CODTIPTIT = TIT.CODTIPTIT ");
		 * sql.append("    AND NVL(TIT.PROIBIMPBOL,'N') = 'N' "); sql.
		 * append("    AND FIN.NUNOTA IN (SELECT NUNOTA FROM TGFVAR WHERE NUNOTAORIG = "
		 * + nunota + ")");
		 */

		ResultSet rs;
		try {
			rs = nativeSql.executeQuery(sql.toString());

			while (rs.next()) {

				BigDecimal nuFin = rs.getBigDecimal("NUFIN");

				System.out.println("nuFin: " + nuFin);

				BoletoHelper.ConfiguracaoBoleto conf = new BoletoHelper.ConfiguracaoBoleto();
				conf.setGerarNumeroBoleto(false);
				conf.setAgrupamentoBoleto(5);
				System.out.println("linha 388");
				conf.setTipoSaidaBoleto(1);
				conf.setFinanceirosSelecionados(Arrays.asList(new BigDecimal[] { nuFin }));
				conf.setReimprimirBoleta(true);
				System.out.println("linha 392");
				BoletoHelper helper = new BoletoHelper();
				helper.gerarBoleto(conf, false, false);

				System.out.println("Chegou aqui");
				
				BoletoHelper boletoHelper = new BoletoHelper();
				boletoHelper.gerarBoleto(conf, false, false);

				byte[] boletoLinha = boletoHelper.getBoletosPDF();

				System.out.println("BOLETO NUFIN:" + nuFin);

				pdfList.addPdfFile(boletoLinha);

			}

		} catch (Exception e2) {
			helper.insereLogTransf(e2.getMessage(), new BigDecimal(0), nunota);
			erro = true;
			e2.printStackTrace();
			
			System.out.println("Fim ---- [ERRO - BOLETO] - erro boleto:" + e2.getMessage());
		}

		try {
			boleto = pdfList.run().toByteArray();

		} catch (Exception e3) {

			helper.insereLogTransf(e3.getMessage(), new BigDecimal(0), nunota);
			erro = true;
			e3.printStackTrace();
			System.out.println("Fim ---- [ERRO - BOLETO] - NÃO GEROU BOLETO:" + e3.getMessage());

		}

		return boleto;

	}

}
