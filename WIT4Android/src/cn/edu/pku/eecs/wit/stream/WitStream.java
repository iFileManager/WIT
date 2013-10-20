package cn.edu.pku.eecs.wit.stream;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import cn.edu.pku.eecs.wit.network.WitProtocol;
import cn.edu.pku.eecs.wit.util.Convertor;

public abstract class WitStream
{
	public enum ObjectType { File, Photo, Music, Contact, App, Error };
	
	protected ObjectType type;
	protected String szName;
	protected String szData;
	protected long lSize, lCompleted;
	protected byte[] arrMD5, arrAES;
	
	protected boolean bStarted = false;
	protected boolean bTerminated = false;
	
	public void startTransmission()
	{
		bStarted = true;
	}
	
	public boolean isStarted()
	{
		return bStarted;
	}
	
	public void terminate()
	{
		bTerminated = true;
	}
	
	public boolean isTerminated()
	{
		return bTerminated;
	}
	
	public ObjectType getType()										//数据类型
	{
		return type;
	}
	
	public String getName()											//数据名
	{
		return szName;
	}
	
	public String getData()											//数据路径
	{
		return szData;
	}
	
	public String getExt()											//获取扩展名
	{
		int nDotIndex = szName.lastIndexOf(".");
		
		if (nDotIndex > -1 && nDotIndex < szName.length() - 1)
		{
			return szName.substring(nDotIndex + 1);
		}
		else
		{
			return "";
		}
	}
	
	public long getSize()
	{
		return lSize;												//数据大小 
	}
	
	public long getCompleted()
	{
		return lCompleted;
	}
	
	public int getPercent()											//获取百分比
	{
		return (int) (lCompleted * 100 / lSize);
	}
	
	public boolean isCompleted()
	{
		return lSize == lCompleted;
	}
	
	public boolean isOverflowed()
	{
		return lSize < lCompleted;
	}
	
	public abstract void close() throws IOException;
	
	protected byte[] getMD5() throws IOException
	{
		final int BUFFER_SIZE = 1048576;
		
		try
		{
			byte[] buffer = new byte[BUFFER_SIZE];
			MessageDigest md5 = MessageDigest.getInstance("MD5");
			
			int nCount;
			
			if (type != ObjectType.Contact)
			{
				FileInputStream fis = new FileInputStream(szData);
				
				while ((nCount = fis.read(buffer)) > 0)
					md5.update(buffer, 0, nCount);
				
				fis.close();
			}
			else md5.update(szData.getBytes());
			
			return md5.digest();
		}
		catch (NoSuchAlgorithmException e)
		{
			return null;
		}
	}
	
	public boolean needsChecksum()
	{
		return arrMD5 != null;
	}
	
	public void buffer(byte[] buffer)
	{
		byte[] bytes;
		
		Arrays.fill(buffer, WitProtocol.TYPE_START, WitProtocol.PACKET_SIZE - 1, (byte) 0);
		
		bytes = Convertor.int2Bytes(Convertor.enum2Int(type));
		System.arraycopy(bytes, 0, buffer, WitProtocol.TYPE_START, bytes.length);
		
		bytes = Convertor.long2Bytes(lSize);
		System.arraycopy(bytes, 0, buffer, WitProtocol.SIZE_START, bytes.length);
		
		if (arrMD5 == null)
			buffer[WitProtocol.MD5_POS] = 1;
		else
			System.arraycopy(arrMD5, 0, buffer, WitProtocol.MD5_START, arrMD5.length);
		
		int nAESKeySize;
		if (arrAES == null)
		{
			nAESKeySize = 0;
			buffer[WitProtocol.AES_POS] = 0;
		}
		else
		{
			nAESKeySize = arrAES.length;
			buffer[WitProtocol.AES_POS] = (byte) arrAES.length;
			System.arraycopy(arrAES, 0, buffer, WitProtocol.AES_START, arrAES.length); 
		}
		
		bytes = szName.getBytes();
		System.arraycopy(bytes, 0, buffer, WitProtocol.AES_START + nAESKeySize, bytes.length);//文件名		
	}
	
	public boolean isEncrypted()
	{
		return arrAES != null;
	}
}
