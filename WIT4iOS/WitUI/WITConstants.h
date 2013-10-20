//
//  WITConstants.h
//  Wit
//
//  Created by Aqua on 13-7-1.
//  Copyright (c) 2013年 __MyCompanyName__. All rights reserved.
//

#ifndef Wit_Constants_h
#define Wit_Constants_h

/* 公共部分 */
#define RESERVED_LEN 3                  //保留3字节
#define TAG_POS 3                       //类型标识位置
#define ID_START 4                      //ID起始位置
#define ID_LEN 8						//ID长度

/* 用户数据包 */
#define IP_START 12                     //IP起始位置
#define IP_LEN 4						//IP长度
#define NAME_START 16                   //用户名起始位置

/* 文件数据包 */
#define PORT_START 12                   //传输端口号起始位置
#define PORT_LEN 4                      //传输端口号长度
#define TYPE_START 16                   //传输类型起始位置
#define TYPE_LEN 4                      //传输类型长度
#define SIZE_START 20                   //数据长度起始位置
#define SIZE_LEN 8                      //数据长度长度
#define FILENAME_START 28               //文件名起始位置

/* 指令 */
#define REG_USER 0                      //注册用户
#define UNREG_USER 1					//更新用户

#define CONNECT 2                       //连接
#define ACCEPT_CONNECT 3				//接受连接
#define REFUSE_CONNECT 4				//拒绝连接
#define DISCONNECT 5					//断开连接

#define SEND_FILENAME 6                 //发送文件名加入列表
#define SEND_FILE 7                     //发送文件请求

#define RECEIVE_SUCCESS 8               //接收成功
#define RECEIVE_FAILED 9				//接收失败

/* 杂项 */
#define PACKET_SIZE 256                 //消息包大小
#define STREAM_BUFFER_SIZE 16384		//流套接字一次传输数据大小

#define PARALLEL_THREADS 5              //同时收发线程数

#define TIMEOUT 15                      //超时用户强制下线时间
#define REFRESH_PERIOD 5                //更新用户数据周期

#define SEND_PORT 8578                  //WIFI通信发送端口
#define RECV_PORT 8578                  //WIFI通信接收端口

#define SHEET_RECEIVE_REQUEST 0         //提示是否接受请求

#define TYPE_FILE 0                     //传输文件
#define TYPE_PHOTO 1                    //传输图片
#define TYPE_CONTACT 2                  //传输联系人

extern const char HEADER[3];            //保留文件头

extern NSString *WITNAME;               //默认名称

extern NSString *NOTIFY_USERLIST_CHANGE;//用户列表改变
extern NSString *NOTIFY_FILELIST_CHANGE;//传输列表改变

extern NSString *NOTIFY_RECEIVE_REQUEST;//收到连接请求
extern NSString *NOTIFY_REMOTE_ACCEPT_REQUEST;//远端接受连接请求
extern NSString *NOTIFY_REMOTE_REFUSE_REQUEST;//远端拒绝连接请求
extern NSString *NOTIFY_DISCONNECT;     //断开连接

extern NSString *NOTIFY_MSG;            //收到消息

extern NSString *NOTIFY_TRANS_SUCCESS;  //传输成功
extern NSString *NOTIFY_TRANS_FAILED;   //传输失败

extern NSString *DEFAULT_USERNAME;      //姓名
extern NSString *DEFAULT_USERID;        //ID

extern NSString *SEND_DIRECTORY;        //发送缓存目录
extern NSString *RECV_DIRECTORY;        //接收缓存目录

#endif
