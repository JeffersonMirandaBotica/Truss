package br.com.sankhya.dctm.envBolAuto;

import java.math.BigDecimal;
import java.sql.Timestamp;

import br.com.sankhya.jape.EntityFacade;
import br.com.sankhya.jape.bmp.PersistentLocalEntity;
import br.com.sankhya.jape.core.JapeSession;
import br.com.sankhya.jape.core.JapeSession.SessionHandle;
import br.com.sankhya.jape.dao.JdbcWrapper;
import br.com.sankhya.jape.sql.NativeSql;
import br.com.sankhya.jape.vo.DynamicVO;
import br.com.sankhya.jape.vo.EntityVO;
import br.com.sankhya.jape.wrapper.JapeFactory;
import br.com.sankhya.modelcore.util.EntityFacadeFactory;

public class EnviaEmailAutoHelper {

	public BigDecimal insereEmail(char[] mensagem, String assunto, String email, BigDecimal codSMTP) throws Exception {

		@SuppressWarnings("unused")
		SessionHandle hnd = null;

		hnd = JapeSession.open();
		EntityFacade dwfFacade = EntityFacadeFactory.getDWFFacade();

		EntityVO entityVO = dwfFacade.getDefaultValueObjectInstance("MSDFilaMensagem");

		DynamicVO dynamicVO = (DynamicVO) entityVO;
		dynamicVO.setProperty("ASSUNTO", assunto);
		dynamicVO.setProperty("DTENTRADA", new Timestamp(System.currentTimeMillis()));
		dynamicVO.setProperty("CODSMTP", codSMTP);
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

		return codFila;
	}

	public void insereAnexo(String nomeArquivo, byte[] arquivo, BigDecimal codFila) throws Exception {

		@SuppressWarnings("unused")
		SessionHandle hnd = null;

		BigDecimal codAnexo = null ;

		SessionHandle anexo = null;
		try {
			anexo = JapeSession.open();
			EntityFacade dwfFacade2 = EntityFacadeFactory.getDWFFacade();
			EntityVO entityVO2 = dwfFacade2.getDefaultValueObjectInstance("AnexoMensagem");
			DynamicVO dynamicVO2 = (DynamicVO) entityVO2;
			dynamicVO2.setProperty("NOMEARQUIVO", nomeArquivo);
			dynamicVO2.setProperty("TIPO", "application/pdf");
			dynamicVO2.setProperty("ANEXO", arquivo);

			PersistentLocalEntity createEntity = dwfFacade2.createEntity("AnexoMensagem", entityVO2);
			DynamicVO save = (DynamicVO) createEntity.getValueObject();

			codAnexo = save.asBigDecimal("NUANEXO");

			System.out.println("nro ANEXO: " + codAnexo);

			JdbcWrapper jdbc = JapeFactory.getEntityFacade().getJdbcWrapper();
			NativeSql nativeSql = new NativeSql(jdbc);

			String sqlAnexo = " INSERT INTO TMDAXM (CODFILA, NUANEXO) VALUES " + "(" + codFila + " , " + codAnexo + ")";
			nativeSql.executeUpdate(sqlAnexo);

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			JapeSession.close(anexo);
		}

	}
	
	public void deletaFila(BigDecimal codFila) throws Exception {
		
		System.out.println("Entrou deletaFila. codFila: " + codFila);
		
		JdbcWrapper jdbc = JapeFactory.getEntityFacade().getJdbcWrapper();
		NativeSql nativeSql = new NativeSql(jdbc);
		
		String sqlAnexo = " DELETE FROM TMDAXM WHERE CODFILA = " + codFila; 
		nativeSql.executeUpdate(sqlAnexo);
		
		String sqlfila = " DELETE FROM TMDFMG WHERE CODFILA = " + codFila; 
		nativeSql.executeUpdate(sqlfila);
	
		
	}

}
