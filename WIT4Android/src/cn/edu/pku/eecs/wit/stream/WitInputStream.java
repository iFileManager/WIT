package cn.edu.pku.eecs.wit.stream;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.security.interfaces.RSAPublicKey;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import cn.edu.pku.eecs.wit.network.WitProtocol;
import cn.edu.pku.eecs.wit.util.Convertor;

public class WitInputStream extends WitStream			//输入流
{
	private FileInputStream fis;
	private Cipher encrypter;
	
	public WitInputStream(ObjectType type, String szName, String szData) throws IOException
	{
		this.type = type;
		this.szName = szName;
		this.szData = szData;
		
		if (type != ObjectType.Contact)
		{
			fis = new FileInputStream(new File(szData));
			lSize = fis.available();					//设置字节数
		}
		else
		{
			lSize = szData.getBytes().length;
		}
		
		arrMD5 = getMD5();
	}
	
	public byte[] read() throws IOException, IllegalBlockSizeException, BadPaddingException
	{
		if (isCompleted()) return null;
		
		
		byte[] buffer;
		
		if (type != ObjectType.Contact)
		{
			int nRealSize = (int) (WitProtocol.STREAM_BUFFER_SIZE + lCompleted < lSize ? 
					WitProtocol.STREAM_BUFFER_SIZE : lSize - lCompleted);
			lCompleted += nRealSize;					//实际读入
			
			buffer = new byte[nRealSize];
			fis.read(buffer);
		}
		else
		{
			lCompleted = lSize;
			buffer = szData.getBytes();
		}
		
		if (encrypter != null) buffer = encrypter.doFinal(buffer);
		return buffer;
	}
	
	@Override
	public void close() throws IOException				//关闭流
	{
		if (fis != null) fis.close();
	}
	
	public void setEncrypted(boolean bEncrypted, RSAPublicKey remoteKey)
	{
		if (!bEncrypted)
		{
			arrAES = null;
			encrypter = null;
		}
		else
		{
			try
			{
				KeyGenerator generator = KeyGenerator.getInstance("AES");
				SecureRandom random = new SecureRandom(Convertor.long2Bytes(
						lSize + System.currentTimeMillis() + (long) (Math.random() * Long.MAX_VALUE)));
				
				generator.init(128, random);
				SecretKey secretKey = generator.generateKey();
				
				byte[] arrRawAES = secretKey.getEncoded();
				
				Cipher encrypter2 = Cipher.getInstance("RSA/ECB/PKCS1Padding", "BC");
				encrypter2.init(Cipher.ENCRYPT_MODE, remoteKey);
				arrAES = encrypter2.doFinal(arrRawAES);
				
				encrypter = Cipher.getInstance("AES");  
	            encrypter.init(Cipher.ENCRYPT_MODE, secretKey);
			}
			catch (Exception e)
			{
				arrAES = null;
				encrypter = null;
			}
		}
	}
}
