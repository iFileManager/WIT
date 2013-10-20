package cn.edu.pku.eecs.wit.network;

public class WitProtocol									//网络协议
{
	public static final int PACKET_SIZE = 256;				//消息包大小
	public static final int STREAM_BUFFER_SIZE = 16384;		//流套接字一次输出数据大小和输入数据大小
	public static final int STREAM_ENCRPYTED_BUFFER_SIZE = 16400;
	
	public static final int TAG_POS = 0;					//类型标识位置
	public static final int ID_START = 1;					//ID起始位置
	public static final int ID_LEN = 8;						//ID长度
	
	public static final int NUMBER_START = 9;				//电话号码起始位置
	public static final int NUMBER_LEN = 8;					//电话号码长度
	public static final int IP_START = 17;					//IP起始位置
	public static final int IP_LEN = 4;						//IP长度
	public static final int RSA_POS = 25;					//RSA密钥长度位置
	public static final int RSA_START = 26;					//RSA密钥起始位置
	
	public static final int PORT_START = 9;					//传输端口号起始位置
	public static final int PORT_LEN = 4;					//传输端口号长度
	public static final int TYPE_START = 13;				//传输类型起始位置
	public static final int TYPE_LEN = 4;					//传输类型长度
	public static final int SIZE_START = 17;				//数据长度起始位置
	public static final int SIZE_LEN = 8;					//数据长度长度
	public static final int MD5_POS = 25;					//是否采用MD5校验位
	public static final int MD5_START = 26;					//MD5验证信息起始位置
	public static final int MD5_LEN = 16;					//MD5验证信息长度
	public static final int AES_POS = 58;					//AES密钥长度位置，与MD5起始位置差32，预留
	public static final int AES_START = 59;					//AES密钥起始位置
	
	public static final int REG_USER = 0;					//注册用户
	public static final int UNREG_USER = 1;					//更新用户
	
	public static final int CONNECT = 2;					//连接
	public static final int ACCEPT_CONNECT = 3;				//接受连接
	public static final int REFUSE_CONNECT = 4;				//拒绝连接
	public static final int DISCONNECT = 5;					//断开连接
	
	public static final int SEND_FILENAME = 6;				//发送文件名加入列表
	public static final int SEND_FILE = 7;					//发送文件请求
	
	public static final int RECEIVE_SUCCESS = 8;			//接收成功
	public static final int RECEIVE_FAILED = 9;				//接收失败
	
	public static final int PARALLEL_THREADS = 5;			//同时收发线程数
		
    public static int TIMEOUT = 15000;						//超时用户强制下线时间
	public static int REFRESH_PERIOD = 5000;				//更新用户数据周期
	
	public static final int CTRL_PORT = 8078;				//WIFI通信端口
	public static final String LOCALHOST = "127.0.0.1";
	
	public static final int ENV_NONE = 0;
	public static final int ENV_WIFI = 1;
	public static final int ENV_HOTSPOT = 2;
}
