package br.com.sankhya.dctm.envBolAuto;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Timestamp;

import org.cuckoo.core.ScheduledAction;
import org.cuckoo.core.ScheduledActionContext;

import com.sankhya.util.BigDecimalUtil;
import com.sankhya.util.TimeUtils;

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
import br.com.sankhya.modelcore.util.EntityFacadeFactory;
import br.com.sankhya.util.ConcatenatePDF;
import br.com.sankhya.ws.ServiceContext;


public class EnviaBoletoAuto implements ScheduledAction {

	ConcatenatePDF arquivos = new ConcatenatePDF();

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

		nativeSql = new NativeSql(jdbc);
		nativeSql.appendSql(" SELECT CAB.NUNOTA ");
		nativeSql.appendSql(" FROM TGFCAB CAB ");
		nativeSql.appendSql(" JOIN TGFTOP TOP ON TOP.CODTIPOPER = CAB.CODTIPOPER AND TOP.DHALTER = CAB.DHTIPOPER ");
		nativeSql.appendSql(" WHERE NVL(TOP.AD_PEDIDOF1,'N')= 'S' ");

		try {
			ResultSet resultado = nativeSql.executeQuery();
			while (resultado.next()) {

				BigDecimal nuNota = resultado.getBigDecimal("NUNOTA");

			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void enviaBoleto() {

		SessionHandle hnd = null;
		String email;

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
			byte[] arquivo = arquivos.run().toByteArray();

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

			} finally {
				JapeSession.close(anexo);
			}

			JdbcWrapper jdbc = JapeFactory.getEntityFacade().getJdbcWrapper();
			NativeSql nativeSql = new NativeSql(jdbc);

			String sqlAnexo = " INSERT INTO TMDAXM (CODFILA, NUANEXO) VALUES " + "(" + codFila + " , " + codAnexo + ")";
			nativeSql.executeUpdate(sqlAnexo);

		} catch (Exception e) {
			e.printStackTrace();
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
			JapeSession.SessionHandle hnd = null;
			hnd = JapeSession.open();
			JapeWrapper impDAO = JapeFactory.dao("AD_LOGGERPEDENTFUT");
			try {
				DynamicVO dynamicVO = impDAO
						.create().set("DHEXEC", TimeUtils.getNow()).set("NUNOTAORIG", nuNotaOrig)
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

}
