//
//  WITOutFile.h
//  Wit
//
//  Created by Aqua on 13-7-3.
//  Copyright (c) 2013年 __MyCompanyName__. All rights reserved.
//

#import <Foundation/Foundation.h>
#import "WITFile.h"
#import "WITUser.h"

@interface WITOutFile : WITFile             //接收文件

- (id) initWithPacket:(NSData *) packet fromUser:(WITUser *) user;//根据数据包创建文件对象
- (void) applyPacket:(NSData *) packet;     //应用数据包

@end
