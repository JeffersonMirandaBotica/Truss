package br.com.sankhya.dctm.envBolAuto;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.cuckoo.core.ScheduledAction;
import org.cuckoo.core.ScheduledActionContext;

import com.sankhya.util.BigDecimalUtil;
import com.sankhya.util.TimeUtils;

import br.com.sankhya.extensions.actionbutton.ContextoAcao;
import br.com.sankhya.extensions.actionbutton.QueryExecutor;
import br.com.sankhya.extensions.actionbutton.Registro;
import br.com.sankhya.jape.EntityFacade;
import br.com.sankhya.jape.bmp.PersistentLocalEntity;
import br.com.sankhya.jape.core.JapeSession;
import br.com.sankhya.jape.core.JapeSession.SessionHandle;
import br.com.sankhya.jape.dao.JdbcWrapper;
import br.com.sankhya.jape.sql.NativeSql;
import br.com.sankhya.jape.util.JapeSessionContext;
import br.com.sankhya.jape.vo.DynamicVO;
import br.com.sankhya.jape.vo.EntityVO;
import br.com.sankhya.jape.wrapper.JapeFactory;
import br.com.sankhya.jape.wrapper.JapeWrapper;
import br.com.sankhya.modelcore.auth.AuthenticationInfo;
import br.com.sankhya.modelcore.comercial.ImpressaoNotaHelpper;
import br.com.sankhya.modelcore.util.ArquivoModeloUtils;
import br.com.sankhya.modelcore.util.DynamicEntityNames;
import br.com.sankhya.modelcore.util.EntityFacadeFactory;
import br.com.sankhya.modelcore.util.Report;
import br.com.sankhya.modelcore.util.ReportManager;
import br.com.sankhya.util.ConcatenatePDF;
import br.com.sankhya.ws.ServiceContext;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperPrint;

public class EnviaBoletoAuto implements ScheduledAction {

	ConcatenatePDF arquivos = new ConcatenatePDF();
	Boolean erro = false;

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

		System.out.println("Entra BuscaFinanceiros");

		try {
			nativeSql = new NativeSql(jdbc);
			nativeSql.appendSql(" SELECT FIN.NUFIN, FIN.NUNOTA ");
			nativeSql.appendSql(" FROM TGFFIN FIN ");
			nativeSql.appendSql(" WHERE NVL(FIN.AD_BOLETOREGISTRADO,'N') = 'S' ");
			nativeSql.appendSql(" AND NVL(FIN.AD_BOLETOIMPRESSO,'N') = 'N' ");
			nativeSql.appendSql(" AND FIN.ORIGEM = 'E' ");

			ResultSet resultado = nativeSql.executeQuery();
			while (resultado.next()) {

				BigDecimal nufin = resultado.getBigDecimal("NUFIN");
				enviaBoleto(nufin);

			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void enviaBoleto(BigDecimal nufin) {

		@SuppressWarnings("unused")
		SessionHandle hnd = null;
		String email = "tales.alves@sankhya.com.br";
		EntityFacade dwfEntityFacade = EntityFacadeFactory.getDWFFacade();

		String corpoemail = "";
		char[] mensagem = corpoemail.toCharArray();

		String assunto = "";

		try {

			hnd = JapeSession.open();
			EntityFacade dwfFacade = EntityFacadeFactory.getDWFFacade();

			EntityVO entityVO = dwfFacade.getDefaultValueObjectInstance("MSDFilaMensagem");

			DynamicVO dynamicVO = (DynamicVO) entityVO;
			dynamicVO.setProperty("ASSUNTO", assunto);
			dynamicVO.setProperty("DTENTRADA", new Timestamp(System.currentTimeMillis()));
			dynamicVO.setProperty("STATUS", "Pendente");
			dynamicVO.setProperty("EMAIL", email);
			dynamicVO.setProperty("TENTENVIO", new BigDecimal(1));
			dynamicVO.setProperty("MENSAGEM", mensagem);
			dynamicVO.setProperty("TIPOENVIO", "E");
			dynamicVO.setProperty("MAXTENTENVIO", new BigDecimal(3));
			dynamicVO.setProperty("CODCON", new BigDecimal(0));

			PersistentLocalEntity createEntity = dwfFacade.createEntity("MSDFilaMensagem", entityVO);
			DynamicVO save = (DynamicVO) createEntity.getValueObject();

			BigDecimal codFila = save.asBigDecimal("CODFILA");

			System.out.println("nro fila: " + codFila);

			arquivos.setNumeration(false);
			byte[] arquivo = null;// arquivos.run().toByteArray();

			SessionHandle anexo = null;
			try {
				anexo = JapeSession.open();
				EntityFacade dwfFacade2 = EntityFacadeFactory.getDWFFacade();
				EntityVO entityVO2 = dwfFacade2.getDefaultValueObjectInstance("AnexoMensagem");
				DynamicVO dynamicVO2 = (DynamicVO) entityVO2;
				dynamicVO2.setProperty("NOMEARQUIVO", "Viagem ");
				dynamicVO2.setProperty("TIPO", "application/pdf");
				dynamicVO2.setProperty("ANEXO", arquivo);

				PersistentLocalEntity createEntity2 = dwfFacade2.createEntity("AnexoMensagem", entityVO2);
				DynamicVO save2 = (DynamicVO) createEntity2.getValueObject();

				codAnexo = save2.asBigDecimal("NUANEXO");

				System.out.println("nro ANEXO: " + codAnexo);

			} catch (Exception e) {
				erro = true;
				e.printStackTrace();
			} finally {
				JapeSession.close(anexo);
			}

			JdbcWrapper jdbc = JapeFactory.getEntityFacade().getJdbcWrapper();
			NativeSql nativeSql = new NativeSql(jdbc);

			String sqlAnexo = " INSERT INTO TMDAXM (CODFILA, NUANEXO) VALUES " + "(" + codFila + " , " + codAnexo + ")";
			nativeSql.executeUpdate(sqlAnexo);

		} catch (Exception e) {
			erro = true;
			e.printStackTrace();
		}

		if (!erro) {

			try {
					
				PersistentLocalEntity persistent = dwfEntityFacade.findEntityByPrimaryKey(DynamicEntityNames.FINANCEIRO, nufin);
				DynamicVO financeiroVO = (DynamicVO) persistent.getValueObject();

					financeiroVO.setProperty("AD_BOLETOIMPRESSO ", "S");
					financeiroVO.setProperty("AD_DHENVBOLETO ",new Timestamp(System.currentTimeMillis()) );
					persistent.setValueObject((EntityVO) financeiroVO);
				

			} catch (Exception e) {
				erro = true;
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

	private void insereLogTransf(String erro, BigDecimal nuNotaOrig, BigDecimal sequenciaOrig, BigDecimal nuNotaGerado,
			BigDecimal sequenciaGerada) {
		try {
			SessionHandle hnd = null;
			hnd = JapeSession.open();
			JapeWrapper impDAO = JapeFactory.dao("AD_LOGGERPEDENTFUT");
			try {
				DynamicVO dynamicVO = impDAO.create().set("DHEXEC", TimeUtils.getNow()).set("NUNOTAORIG", nuNotaOrig)
						.set("SEQUENCIAORIG", sequenciaOrig).set("NUNOTAGERADO", nuNotaGerado)
						.set("SEQUENCIAGERADA", sequenciaGerada).set("ERRO", erro).save();
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				JapeSession.close(hnd);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	@SuppressWarnings("static-access")
	private void impressaoNota(ContextoAcao ctx, Registro line) {

		EntityFacade dwfEntityFacade = EntityFacadeFactory.getDWFFacade();

		JdbcWrapper jdbc = dwfEntityFacade.getJdbcWrapper();

		try {

			jdbc.openSession();

			BigDecimal nuNota = (BigDecimal) line.getCampo("NUNOTA");

			System.out.println("nuNota: " + nuNota);

			BigDecimal nroRelatorio = new BigDecimal(0);
			Map<String, Object> parameters = new HashMap<>();
			Report modeloImpressao = null;

			byte[] arqFatura = null;

			// busca os NUNOTA
			QueryExecutor query = ctx.getQuery();

			StringBuffer sqlQuery = new StringBuffer("");
			sqlQuery.append(" SELECT CAB.NUNOTA, CAB.NUMNOTA, TP.CODMODDOC, ");
			sqlQuery.append("	ISNULL(CAB.STATUSNFSE,'N') AS STATUSNFSE, ");
			sqlQuery.append("	(SELECT NURFE FROM TGFMON WHERE CODMODNF = ISNULL(NUM.MODNOTAFIS,TP.CODMODNF)) NURFE ");
			sqlQuery.append(" FROM TGFCAB CAB ");
			sqlQuery.append(" JOIN TGFTOP TP ON CAB.CODTIPOPER = TP.CODTIPOPER AND CAB.DHTIPOPER = TP.DHALTER ");
			sqlQuery.append(" LEFT JOIN TGFNUM NUM ");
			sqlQuery.append(
					" ON ARQUIVO = 'VENDA' AND CAB.SERIENOTA = NUM.SERIE AND CAB.CODEMP = NUM.CODEMP AND TP.CODMODDOC = NUM.CODMODDOC ");
			sqlQuery.append(" WHERE CAB.NUNOTA = " + nuNota + " ");

			query.nativeSelect(sqlQuery.toString());

			while (query.next()) {

				String statusNFSe = query.getString("STATUSNFSE");

				String codModDoc = query.getString("CODMODDOC");

				System.out.println("nuNota: " + nuNota);
				System.out.println("statusNFSe: " + statusNFSe);
				System.out.println("codModDoc: " + codModDoc);

			
					JapeWrapper notaDAO = JapeFactory.dao("CabecalhoNota");
					Collection<DynamicVO> notasVO = notaDAO.find("NUNOTA = ?", nuNota);
					for (@SuppressWarnings("unused")
					DynamicVO notaVO : notasVO) {

						BigDecimal numeroNota = (BigDecimal) notaVO.getProperty("NUNOTA");

						// inicio gera��o do relat�rio fatura
						nroRelatorio = query.getBigDecimal("NURFE");
						parameters = new HashMap<>();

						@SuppressWarnings("unused")
						ImpressaoNotaHelpper impressaoNotaHelpper = new ImpressaoNotaHelpper(); // getImagemQRCodeDanfeCTe;

						parameters.put("NUNOTA", numeroNota);
						parameters.put("PDIR_MODELO", ArquivoModeloUtils.getDiretorioModelos());

						modeloImpressao = ReportManager.getInstance().getReport(nroRelatorio, dwfEntityFacade);

						JasperPrint jasperPrint = null;

						jasperPrint = modeloImpressao.buildJasperPrint(parameters, jdbc.getConnection());

						arqFatura = JasperExportManager.exportReportToPdf(jasperPrint);

						arquivos.addPdfFile(arqFatura);

						arqFatura = null;

					
				}

			}

		} catch (

		Exception e) {
			jdbc.closeSession();
			e.printStackTrace();
		} finally {
			jdbc.closeSession();
		}
	}


}
