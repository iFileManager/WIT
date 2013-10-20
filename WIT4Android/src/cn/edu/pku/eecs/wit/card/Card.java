package cn.edu.pku.eecs.wit.card;

import java.io.Serializable;
import java.net.InetAddress; 
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;


import cn.edu.pku.eecs.wit.network.WitProtocol;
import cn.edu.pku.eecs.wit.util.Convertor;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.telephony.TelephonyManager;

public class Card implements Serializable										//名片
{
	private static final long serialVersionUID = 6525527209173193298L;
	private static Card myCard;
	
	public static void setMyCard(Card myCard_)									//设置、获得本人名片
	{
		myCard = myCard_;
	}
	
	public static Card getMyCard()
	{
		return myCard; 
	}
		
    private String szName="";
    private long lNumber;
    
    private String szIP="";
    
    private long lID;
    private long lStamp;
    
    private byte[] arrPublicKey, arrPrivateKey;
    
    public Card(Context context)
    {
    	TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
    	
    	String szPhoneNumber = tm.getLine1Number();
        if(szPhoneNumber != null && !szPhoneNumber.trim().equals(""))
        {
        	szPhoneNumber = szPhoneNumber.replaceAll("[^0-9]", "");
        	if (szPhoneNumber.startsWith("86") && szPhoneNumber.length() == 13)
        		szPhoneNumber = szPhoneNumber.substring(2);
        	
        	try
        	{
        		lNumber = Long.parseLong(szPhoneNumber);
        	}
        	catch (Exception e)
        	{
        		lNumber = 0;
        	}
        }
        else lNumber = 0;
        
        try
        {
        	String szId = tm.getDeviceId();
        	lID = Long.parseLong(szId);
        }
        catch (Exception e)
        {
        	lID = (long) (Math.random() * Long.MAX_VALUE);
        }
        
		try
		{
			SecureRandom random = new SecureRandom(Convertor.long2Bytes(
				lID + System.currentTimeMillis() + (long) (Math.random() * Long.MAX_VALUE)));
			
			KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");		
			generator.initialize(1024, random);
			
			KeyPair pair = generator.generateKeyPair();
			
			RSAPublicKey publicKey = (RSAPublicKey) pair.getPublic();
			RSAPrivateKey privateKey = (RSAPrivateKey) pair.getPrivate();
			
			arrPublicKey = publicKey.getEncoded();
			arrPrivateKey = privateKey.getEncoded();
		}
		catch (Exception e)
		{
			arrPublicKey = arrPrivateKey = null;
		}
    }
    
    public Card(byte[] buffer)													//从缓冲区重建
    {
    	byte[] bytes;
    	
    	bytes = new byte[WitProtocol.ID_LEN];
    	System.arraycopy(buffer, WitProtocol.ID_START, bytes, 0, bytes.length);
    	lID = Convertor.bytes2Long(bytes);
    	
    	bytes = new byte[WitProtocol.NUMBER_LEN];
    	System.arraycopy(buffer, WitProtocol.NUMBER_START, bytes, 0, bytes.length);
    	lNumber = Convertor.bytes2Long(bytes);
    	
    	try
    	{
	    	bytes = new byte[WitProtocol.IP_LEN];
	    	System.arraycopy(buffer, WitProtocol.IP_START, bytes, 0, bytes.length);
			szIP = InetAddress.getByAddress(bytes).getHostAddress();
    	}
    	catch (Exception e) { }
    	
    	int nPublicKeySize = buffer[WitProtocol.RSA_POS];
    	if (nPublicKeySize == 0)
    	{
    		arrPublicKey = null;
    	}
    	else
    	{
    		if (nPublicKeySize < 0) nPublicKeySize += 256;
    		
    		arrPublicKey = new byte[nPublicKeySize];
    		System.arraycopy(buffer, WitProtocol.RSA_START, arrPublicKey, 0, arrPublicKey.length);
    	}
    	
    	arrPrivateKey = null;
    	
    	int nUserNameStart = WitProtocol.RSA_START + nPublicKeySize;
		bytes = new byte[WitProtocol.PACKET_SIZE - nUserNameStart];
		System.arraycopy(buffer, nUserNameStart, bytes, 0, bytes.length);
		szName = new String(bytes).trim();
		
		lStamp = System.currentTimeMillis();
    }
    
    private String getUnnullableString(String szNullable)
    {
    	return szNullable == null ? "" : szNullable;							//不可空
    }
    
    public long getID()
    {
    	return lID;
    }
    
    public void setName(String szName)
    {
    	this.szName = getUnnullableString(szName);
    }
    
    public String getName()
    {
    	return getUnnullableString(szName);
    }
    
    public void setNumber(long lNumber)
    {
    	this.lNumber = lNumber;
    }
    
    public long getNumber()
    {
    	return lNumber;
    }
    
    public String getIP()
    {
    	return getUnnullableString(szIP);
    }
    
    public long getStamp()
    {
    	return lStamp;
    }
	
	public void setIP(String szIP)
	{
		this.szIP = szIP;
	}
	
	public void createContact(Context context)									//创建联系人
	{
		ContentResolver resolver = context.getContentResolver();
		ContentValues values = new ContentValues();
        Uri uri = resolver.insert(RawContacts.CONTENT_URI, values);
        long nID = ContentUris.parseId(uri);									//获取ID

        values.clear();
        values.put(Data.RAW_CONTACT_ID, nID);
        values.put(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE);
        values.put(StructuredName.DISPLAY_NAME, getName());
        resolver.insert(Data.CONTENT_URI, values);								//插入姓名
        
        values.clear();
        values.put(Data.RAW_CONTACT_ID, nID);
        values.put(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
        values.put(Phone.TYPE, Phone.TYPE_MOBILE);
        values.put(Phone.NUMBER, Long.toString(getNumber()));
        values.put(Data.IS_PRIMARY, 1);
        resolver.insert(Data.CONTENT_URI, values);								//插入号码
	}
	
	public RSAPublicKey getPublicKey()
	{
		try
		{
			X509EncodedKeySpec keySpec = new X509EncodedKeySpec(arrPublicKey);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            RSAPublicKey publicKey = (RSAPublicKey) keyFactory.generatePublic(keySpec);   
            
            return publicKey;   
		}
		catch (Exception e)
		{
			return null;
		}
	}
	
	public RSAPrivateKey getPrivateKey()
	{
		try
		{
			PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(arrPrivateKey);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            RSAPrivateKey privateKey = (RSAPrivateKey) keyFactory.generatePrivate(keySpec);
            
            return privateKey;   
		}
		catch (Exception e)
		{
			return null;
		}
	}
	
	public void buffer(byte[] buffer)
	{
		byte[] bytes;
		
		Arrays.fill(buffer, (byte) 0);
		
		bytes = Convertor.long2Bytes(lID);
		System.arraycopy(bytes, 0, buffer, WitProtocol.ID_START, bytes.length);
		
		bytes = Convertor.long2Bytes(lNumber);
		System.arraycopy(bytes, 0, buffer, WitProtocol.NUMBER_START, bytes.length);
		
		try
		{
			bytes = InetAddress.getByName(szIP).getAddress();
			System.arraycopy(bytes, 0, buffer, WitProtocol.IP_START, bytes.length);
		}
		catch (Exception e) { }
		
		int nPublicKeySize;
		if (arrPublicKey == null)
		{
			nPublicKeySize = 0;
			buffer[WitProtocol.RSA_POS] = 0;
		}
		else
		{
			nPublicKeySize = arrPublicKey.length;
			buffer[WitProtocol.RSA_POS] = (byte) arrPublicKey.length;
			System.arraycopy(arrPublicKey, 0, buffer, WitProtocol.RSA_START, arrPublicKey.length); 
		}
		
		bytes = szName.getBytes();
		System.arraycopy(bytes, 0, buffer, WitProtocol.RSA_START + nPublicKeySize, bytes.length);
	}
}
