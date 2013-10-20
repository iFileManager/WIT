//
//  WITNetworkEnv.m
//  Wit
//
//  Created by Aqua on 13-7-1.
//  Copyright (c) 2013年 __MyCompanyName__. All rights reserved.
//

#import "WITNetworkEnv.h"
#import "WITConstants.h"
#import "WITUtils.h"

#import <stdio.h>  
#import <stdlib.h>
#import <string.h>  
#import <unistd.h>  
#import <sys/ioctl.h>
#import <sys/sysctl.h>
#import <sys/types.h>  
#import <sys/socket.h>  
#import <netinet/in.h>  
#import <netdb.h>  
#import <arpa/inet.h>  
#import <sys/sockio.h>  
#import <net/if.h>
#import <net/ethernet.h>
#import <errno.h>  
#import <net/if_dl.h>  
#import <ifaddrs.h>

#define min(a,b)    ((a) < (b) ? (a) : (b))  
#define max(a,b)    ((a) > (b) ? (a) : (b))  

#define BUFFERSIZE  4000  
#define MAXADDRS 20

@implementation WITNetworkEnv

@synthesize closed;

- (NSString *) username                         //用户名
{
    return theUsername;
}

- (void) setUsername:(NSString *)username       //设置用户名
{
    theUsername = username;                     //更新UserDefaults
    
    NSUserDefaults *defaults = [NSUserDefaults standardUserDefaults];
    [defaults setObject:username
                 forKey:DEFAULT_USERNAME];
    [defaults synchronize];
    
    [self generatePacket];
}

- (id) initWithService:(WITService *) service_  //初始化
{
    self = [super init];
    
    if (self)                                   //注册默认设置
    {
        NSMutableDictionary *defaultDict = [[NSMutableDictionary alloc] init];
        
        [defaultDict setObject:WITNAME forKey:DEFAULT_USERNAME];
        [defaultDict setObject:[NSNumber numberWithUnsignedLong:0] forKey:DEFAULT_USERID];
        
        NSUserDefaults *defaults = [NSUserDefaults standardUserDefaults];
        [defaults registerDefaults:defaultDict];
        
        service = service_;
        
        sendSocket = nil;
        closed = NO;
        
        srandom(clock());
        
        brdAddr = @"255.255.255.255";           //广播地址
        theUsername = [defaults objectForKey:DEFAULT_USERNAME];
        
        [self calcID];                          //计算ID
        
        [self createSocket];                    //创建套接字
        
        [self generatePacket];                  //生成注册包
        
        timer = [NSTimer scheduledTimerWithTimeInterval: REFRESH_PERIOD
                                                 target: self
                                               selector: @selector(broadcastMyself:)
                                               userInfo: nil
                                                repeats: YES];
                                                //定期广播自己
        [self performSelectorInBackground:@selector(receiveLoop) withObject:nil];
    }                                           //同步接收UDP信息
    
    return self;
}

- (void) close                                  //关闭
{
    if (!closed)                                //已经关闭
    {
        [timer invalidate];                     //停止刷新
        
        NSData *newPacket = self.packet;
        
        Byte *buffer = (Byte *) [newPacket bytes];
        buffer[TAG_POS] = UNREG_USER;
        
        [self sendBroadcast:newPacket];         //广播退出消息
        
        closed = YES;
    }
                                                //关闭套接字
    if (![sendSocket isClosed]) [sendSocket close];
    if (![recvSocket isClosed]) [recvSocket close];
}

- (void) createSocket                           //创建套接字
{
    sendSocket = [[WITSyncUdpSocket alloc] initWithDelegate:self];
    recvSocket = [[WITSyncUdpSocket alloc] initWithDelegate:self];
    
    NSError *err = nil;
    
    if (![recvSocket bindToPort:RECV_PORT error:&err])
    {
        NSNotificationCenter *center = [NSNotificationCenter defaultCenter];
        [center postNotificationName:NOTIFY_RECEIVE_REQUEST
                              object:self
                            userInfo:[NSDictionary dictionaryWithObject:
                                      [NSString stringWithFormat:@"绑定%u端口失败", RECV_PORT]
                                                                 forKey:@"msg"]];
        closed = YES;
    }                                           //绑定端口
    
    if (![sendSocket enableBroadcast:YES error:&err])
    {
        NSNotificationCenter *center = [NSNotificationCenter defaultCenter];
        [center postNotificationName:NOTIFY_RECEIVE_REQUEST
                              object:self
                            userInfo:[NSDictionary dictionaryWithObject:@"启用广播失败"
                                                                 forKey:@"msg"]];
        closed = YES;
    }                                           //允许广播
}

- (void) generatePacket                         //生成数据包
{
    Byte *buffer, *bytes;
    
    buffer = (Byte *)malloc(PACKET_SIZE);
    
    [self calcIP];
    
    memset(buffer, 0, PACKET_SIZE);
    
    memcpy(buffer, HEADER, RESERVED_LEN);       //头部
    buffer[TAG_POS] = REG_USER;                 //标志
    
    bytes = [WITUtils convertUnsignedLong2Bytes:userId];  
    memcpy(buffer + ID_START, bytes, ID_LEN);   //ID
    free(bytes);
    
    bytes = [WITUtils convertIPString2Bytes:ipAddr];
    memcpy(buffer + IP_START, bytes, IP_LEN);   //IP
    free(bytes);
    
    bytes = [WITUtils convertString2Bytes:theUsername withMaxLength:PACKET_SIZE - NAME_START];
    memcpy(buffer + NAME_START, bytes, PACKET_SIZE - NAME_START);
    free(bytes);                                //用户名
    
    packet = [[NSData alloc] initWithBytes:buffer length:PACKET_SIZE];
    free(buffer);
    
    [self broadcastMyself:nil];                 //立即广播
}

- (NSData *) packet                             //获取数据包的拷贝
{
    Byte *buffer = (Byte *) [packet bytes];    
    NSData *newPacket = [[NSData alloc] initWithBytes:buffer length:PACKET_SIZE];
    return newPacket;
}

- (void) broadcastMyself:(NSTimer *) timer      //广播自己
{
    [self sendBroadcast:packet];
}

- (void) sendBroadcast:(NSData *) data          //广播
{
    if (closed) return;
    [sendSocket doSyncSend:data toHost:brdAddr port:SEND_PORT];
}

- (void) send:(NSData *) data                   //单播
       to:(WITUser *) user
{
    if (closed) return;
    [sendSocket doSyncSend:data toHost:user.ipAddr port:SEND_PORT];
}

- (void) receiveLoop                            //循环接收
{
    while (!closed)
    {                                           //调用接收函数
        NSData *data = [recvSocket doSyncReceive];
        
        if (!closed && data != nil && [data length] == PACKET_SIZE)
        {                                       //数据必须符合要求
            Byte *buffer, *bytes;
            
            buffer = (Byte *) [data bytes];
            
            if (memcmp(buffer, HEADER, RESERVED_LEN) == 0)
            {                                   //检查数据头
                bytes = (Byte *) malloc(ID_LEN);
                
                memcpy(bytes, buffer + ID_START, ID_LEN);
                unsigned long otherId = [WITUtils convertBytes2UnsignedLong:bytes];
                free(bytes);
                
                if (otherId != userId)          //必须不是自己发的
                {
                    [service decode:data forUser:otherId];
                }
            }
        }
    }
}

- (void) calcIP                                 //计算IP
{
    char *if_names[MAXADDRS];  
    char *ip_names[MAXADDRS];
    
    int                 i, len, flags;  
    char                buffer[BUFFERSIZE], *ptr, lastname[IFNAMSIZ], *cptr;  
    struct ifconf       ifc;  
    struct ifreq        *ifr, ifrcopy;  
    struct sockaddr_in  *sin;
    
    int nextAddr = 0;  
    
    char temp[80];  
    
    int sockfd;  
    
    for (i=0; i<MAXADDRS; ++i)  
    {  
        if_names[i] = ip_names[i] = NULL;
    }  
    
    sockfd = socket(AF_INET, SOCK_DGRAM, 0);  
    if (sockfd < 0)  
    {  
        perror("socket failed");  
        ipAddr = @"127.0.0.1";
        return;
    }  
    
    ifc.ifc_len = BUFFERSIZE;  
    ifc.ifc_buf = buffer;  
    
    if (ioctl(sockfd, SIOCGIFCONF, &ifc) < 0)  
    {  
        perror("ioctl error");
        ipAddr = @"127.0.0.1";
        return;  
    }  
    
    lastname[0] = 0;  
    
    for (ptr = buffer; ptr < buffer + ifc.ifc_len; )  
    {  
        ifr = (struct ifreq *)ptr;  
        len = max(sizeof(struct sockaddr), ifr->ifr_addr.sa_len);  
        ptr += sizeof(ifr->ifr_name) + len;  // for next one in buffer  
        
        if (ifr->ifr_addr.sa_family != AF_INET)  
        {  
            continue;   // ignore if not desired address family  
        }  
        
        if ((cptr = (char *)strchr(ifr->ifr_name, ':')) != NULL)  
        {  
            *cptr = 0;      // replace colon will null  
        }  
        
        if (strncmp(lastname, ifr->ifr_name, IFNAMSIZ) == 0)  
        {  
            continue;   /* already processed this interface */  
        }  
        
        memcpy(lastname, ifr->ifr_name, IFNAMSIZ);  
        
        ifrcopy = *ifr;  
        ioctl(sockfd, SIOCGIFFLAGS, &ifrcopy);  
        flags = ifrcopy.ifr_flags;  
        if ((flags & IFF_UP) == 0)  
        {  
            continue;   // ignore if interface not up  
        }  
        
        if_names[nextAddr] = (char *)malloc(strlen(ifr->ifr_name)+1);  
        if (if_names[nextAddr] == NULL)  
        {  
            break;  
        }  
        strcpy(if_names[nextAddr], ifr->ifr_name);  
        
        sin = (struct sockaddr_in *)&ifr->ifr_addr;  
        strcpy(temp, inet_ntoa(sin->sin_addr));  
        
        ip_names[nextAddr] = (char *)malloc(strlen(temp)+1);  
        if (ip_names[nextAddr] == NULL)  
        {  
            break;   
        }  
        strcpy(ip_names[nextAddr], temp);
        
        ++nextAddr;  
    }  
    
    close(sockfd);
    
    if (ip_names[1] != NULL) ipAddr = [NSString stringWithFormat:@"%s", ip_names[1]];
    else ipAddr = @"127.0.0.1";
    
    for (i=0; i<MAXADDRS; ++i)  
    {  
        if (if_names[i] != NULL) free(if_names[i]);  
        if (ip_names[i] != NULL) free(ip_names[i]);
    }
}

- (void) calcID                                 //通过Mac地址计算ID
{
    int                 mgmtInfoBase[6];
    char                *msgBuffer = NULL;
    size_t              length;
    unsigned char       macAddress[6];
    struct if_msghdr    *interfaceMsgStruct;
    struct sockaddr_dl  *socketStruct;
    NSString            *errorFlag = NULL;
    
    // Setup the management Information Base (mib)
    mgmtInfoBase[0] = CTL_NET;        // Request network subsystem
    mgmtInfoBase[1] = AF_ROUTE;       // Routing table info
    mgmtInfoBase[2] = 0;              
    mgmtInfoBase[3] = AF_LINK;        // Request link layer information
    mgmtInfoBase[4] = NET_RT_IFLIST;  // Request all configured interfaces
    
    // With all configured interfaces requested, get handle index
    if ((mgmtInfoBase[5] = if_nametoindex("en0")) == 0) 
        errorFlag = @"if_nametoindex failure";
    else
    {
        // Get the size of the data available (store in len)
        if (sysctl(mgmtInfoBase, 6, NULL, &length, NULL, 0) < 0) 
            errorFlag = @"sysctl mgmtInfoBase failure";
        else
        {
            // Alloc memory based on above call
            if ((msgBuffer = malloc(length)) == NULL)
                errorFlag = @"buffer allocation failure";
            else
            {
                // Get system information, store in buffer
                if (sysctl(mgmtInfoBase, 6, msgBuffer, &length, NULL, 0) < 0)
                    errorFlag = @"sysctl msgBuffer failure";
            }
        }
    }
    // Befor going any further...
    if (errorFlag != NULL)
    {
        NSLog(@"Error: %@", errorFlag);
        userId = random();
    }
    else
    {
        // Map msgbuffer to interface message structure
        interfaceMsgStruct = (struct if_msghdr *) msgBuffer;
        // Map to link-level socket structure
        socketStruct = (struct sockaddr_dl *) (interfaceMsgStruct + 1);  
        // Copy link layer address data in socket structure to an array
        memcpy(&macAddress, socketStruct->sdl_data + socketStruct->sdl_nlen, 6);  
        // Read from char array into a string object, into traditional Mac address format
        
        userId = 0;
        int i;
        for (i = 0; i < 6; ++i)
        {
            userId <<= 8;
            userId += macAddress[i];
        }
    }
    // Release the buffer memory    
    free(msgBuffer);
    
    NSUserDefaults *defaults = [NSUserDefaults standardUserDefaults];
    [defaults setObject:[NSNumber numberWithUnsignedLong:userId]
                 forKey:DEFAULT_USERID];
    [defaults synchronize];
}

@end
