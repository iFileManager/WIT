package cn.edu.pku.eecs.wit.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.StreamCorruptedException;

import cn.edu.pku.eecs.wit.stream.WitStream.ObjectType;

import android.util.Base64;

public class Convertor
{
	public static int enum2Int(ObjectType type)
	{
		switch (type)
		{
		case App: return 0;
		case Contact: return 1;
		case File: return 2;
		case Music: return 3;
		case Photo: return 4;
		default: break;
		}
		return -1;
	}
	
	public static ObjectType int2Enum(int i)
	{
		switch (i)
		{
			case 0: return ObjectType.App;
			case 1: return ObjectType.Contact;
			case 2: return ObjectType.File;
			case 3: return ObjectType.Music;
			case 4: return ObjectType.Photo;
		}
		return ObjectType.Error;
	}

	public static byte[] int2Bytes(int value)
	{
		byte[] bytes = new byte[4];
		for (int i = 0; i < 4; i++) {
			int offset = (bytes.length - 1 - i) * 8;
			bytes[i] = (byte) ((value >>> offset) & 0xFF);
		}
		return bytes;
	}
	
	public static final int bytes2Int(byte[] b)
	{
		return (b[0] << 24) + ((b[1] & 0xFF) << 16) + ((b[2] & 0xFF) << 8) + (b[3] & 0xFF);
	}

	public static byte[] long2Bytes(long a)
	{
		byte[] bytes = new byte[8];
		for (int i = 0; i < bytes.length; i++)
		{
			int offset = (7 - i) * 8;
			bytes[i] = (byte) ((a >>> offset) & 0xFF);
		}
		return bytes;
	}

	public static long bytes2Long(byte[] bArray)
	{
		long ret = 0;
		for (int i = 0; i < bArray.length; i++) {
			ret += ((long) (bArray[i] & 0XFF)) << (8 * (7 - i));
		}
		return ret;
	}
	
	public static byte[] obj2Bytes(Object obj) throws IOException
	{
		byte[] ret;
		
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		ObjectOutputStream oos = new ObjectOutputStream(bos);
		
		oos.writeObject(obj);
		ret = bos.toByteArray();
		
		oos.close();
		bos.close();
		
		return ret;
	}
	
	public static Object bytes2Obj(byte[] bytes) throws StreamCorruptedException, IOException, ClassNotFoundException
	{
		Object ret;
		
		ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
		ObjectInputStream ois = new ObjectInputStream(bis);
		
		ret = ois.readObject();
		
		ois.close();
		bis.close();
		
		return ret;
	}
	
	public static String obj2String(Object obj) throws RuntimeException, IOException
	{
		return Base64.encodeToString(obj2Bytes(obj), Base64.DEFAULT);
	}
	
	public static Object string2Obj(String szObj) throws StreamCorruptedException, IOException, ClassNotFoundException, RuntimeException
	{
		return bytes2Obj(Base64.decode(szObj.getBytes(), Base64.DEFAULT));
	}

	public static String macBytes2String(byte[] arrMacAddress)
	{
		String szRet = "";
		
		for (int i = 0; i < arrMacAddress.length; ++i)
		{
			if (szRet.length() > 0) szRet += ":";
			szRet += String.format("%02x", arrMacAddress[i]);
		}
		
		return szRet;
	}
}
