package cn.edu.pku.eecs.wit.stream;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.interfaces.RSAPrivateKey;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import cn.edu.pku.eecs.wit.WitActivity;
import cn.edu.pku.eecs.wit.network.WitProtocol;
import cn.edu.pku.eecs.wit.network.WitService;
import cn.edu.pku.eecs.wit.util.Convertor;


import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;

public class WitOutputStream extends WitStream	//输出流
{
	private FileOutputStream fos;
	private Cipher decrypter;
	
	public WitOutputStream(byte[] buffer) throws IOException
	{
		byte[] bytes;
		
		bytes = new byte[WitProtocol.TYPE_LEN];
		System.arraycopy(buffer, WitProtocol.TYPE_START, bytes, 0, bytes.length);
		type = Convertor.int2Enum(Convertor.bytes2Int(bytes));
		
		bytes = new byte[WitProtocol.SIZE_LEN];
		System.arraycopy(buffer, WitProtocol.SIZE_START, bytes, 0, bytes.length);
		lSize = Convertor.bytes2Long(bytes);
		
		if (buffer[WitProtocol.MD5_POS] == 1) arrMD5 = null;
		else
		{
			arrMD5 = new byte[WitProtocol.MD5_LEN];
			System.arraycopy(buffer, WitProtocol.MD5_START, arrMD5, 0, arrMD5.length);
		}
		
		int nAESKeySize = buffer[WitProtocol.AES_POS];
		if (nAESKeySize == 0)
		{
    		arrAES = null;
		}
    	else
    	{
    		if (nAESKeySize < 0) nAESKeySize += 256;
    		
    		arrAES = new byte[nAESKeySize];
    		System.arraycopy(buffer, WitProtocol.AES_START, arrAES, 0, arrAES.length);
    	}
		
		int nFileNameStart = WitProtocol.AES_START + nAESKeySize;
		bytes = new byte[WitProtocol.PACKET_SIZE - nFileNameStart];
		System.arraycopy(buffer, nFileNameStart, bytes, 0, bytes.length);
		szName = new String(bytes).trim();
		
		szData = WitService.getSaveDir() + File.separator + szName;
		
		if (type != ObjectType.Contact)
		{
			File file = new File(szData);
			fos = new FileOutputStream(file);
		}
	}
	
	public void write(byte[] buffer) throws IOException, IllegalBlockSizeException, BadPaddingException
	{
		if (isCompleted()) return;
		
		if (decrypter != null) buffer = decrypter.doFinal(buffer);
		
		if (type == ObjectType.Contact)
		{
			szData = new String(buffer);
			lCompleted = lSize;
		}
		else
		{
			lCompleted += buffer.length;
			fos.write(buffer);							//写数据
		}
	}

	@Override
	public void close() throws IOException 
	{
		if (fos != null) fos.close();
	}
	
	public void doAfterTransmission(Context context)
	{
		if (type == ObjectType.Contact)					//保存通讯录
		{
			
			ContentResolver resolver = context.getContentResolver();
			ContentValues values = new ContentValues();
	        Uri uri = resolver.insert(RawContacts.CONTENT_URI, values);
	        long nID = ContentUris.parseId(uri);

	        values.clear();
	        values.put(Data.RAW_CONTACT_ID, nID);
	        values.put(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE);
	        values.put(StructuredName.DISPLAY_NAME, szName);
	        resolver.insert(Data.CONTENT_URI, values);
	        
	        int nTag = 1;
	        for (String szNumber : szData.split(" "))
	        {
	        	if (szNumber.trim().equals("")) continue;
	        	
	        	values.clear();
		        values.put(Data.RAW_CONTACT_ID, nID);
		        values.put(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
		        values.put(Phone.TYPE, Phone.TYPE_MOBILE);
		        values.put(Phone.NUMBER, szNumber);
		        values.put(Data.IS_PRIMARY, nTag); nTag *= 0;
		        resolver.insert(Data.CONTENT_URI, values);
	        }
		}
		else
		{
			if (type == ObjectType.App)
			{
				try
				{
					File file = new File(szData);
					
					Intent intent = new Intent(Intent.ACTION_VIEW);
					intent.setDataAndType(Uri.fromFile(file), "application/vnd.android.package-archive");
					WitActivity.getSingleton().startActivity(intent);			//安装程序
				}
				catch (Exception e) { }
			}
			else											//刷新媒体库
			{
				context.sendBroadcast(new Intent(Intent.ACTION_MEDIA_MOUNTED, 
					Uri.parse("file://" + Environment.getExternalStorageDirectory())));
			}
		}
	}
	
	public void delete()
	{
		File file = new File(szData);
		if (file.exists()) file.delete();
	}
	
	public boolean checksum()
	{
		try
		{
			byte[] arrLocalMD5 = getMD5();
			if (arrLocalMD5 == null) return false;
			
			return MessageDigest.isEqual(arrMD5, arrLocalMD5);
		}
		catch (Exception e)
		{
			return false;
		}
	}
	
	public void decryptSecretKey(RSAPrivateKey myKey)
	{
		try
		{
			Cipher decrypter2 = Cipher.getInstance("RSA/ECB/PKCS1Padding", "BC");
			decrypter2.init(Cipher.DECRYPT_MODE, myKey);
			
			byte[] arrRawAES = decrypter2.doFinal(arrAES);
			SecretKey secretKey = new SecretKeySpec(arrRawAES, "AES");
			
			decrypter = Cipher.getInstance("AES");  
            decrypter.init(Cipher.DECRYPT_MODE, secretKey);
		}
		catch (Exception e)
		{
			arrAES = null;
			decrypter = null;
		}
	}
}
