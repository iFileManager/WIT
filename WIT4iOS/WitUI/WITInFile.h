//
//  WITInFile.h
//  Wit
//
//  Created by Aqua on 13-7-3.
//  Copyright (c) 2013年 __MyCompanyName__. All rights reserved.
//

#import <Foundation/Foundation.h>
#import "WITFile.h"

@interface WITInFile : WITFile          //发送文件
{
    GCDAsyncSocket *client;             //对方向本方端口连接后创建的套接字
}

@property (readonly) NSData *packet;    //将发送的数据包

- (id) initWithFile:(NSString *) _filepath ofType:(unsigned) _type toUser:(WITUser *) user;
                                        //初始化，包括路径，类型，对方用户
@end
