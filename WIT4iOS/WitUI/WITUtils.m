//
//  WITUtils.m
//  Wit
//
//  Created by Aqua on 13-7-1.
//  Copyright (c) 2013年 __MyCompanyName__. All rights reserved.
//

#import "WITUtils.h"
#import "WITConstants.h"

#import <arpa/inet.h>

@implementation WITUtils

+ (Byte *) convertString2Bytes:(NSString *) string withMaxLength:(unsigned) len
{                                       //字符串转字节数组，限定长度为len - 1
    Byte * ret = (Byte *) malloc(len);
    memset(ret, 0, len);
    
    const char * cstring = [string cStringUsingEncoding:NSUTF8StringEncoding];
    int minLen = strlen(cstring);
    
    if (minLen > len - 1) minLen = len - 1;
    memcpy(ret, cstring, minLen);
    
    return ret;
}

+ (Byte *) convertUnsignedLong2Bytes:(unsigned long) number
{                                       //无符号长整型转字节数组
    Byte * ret = (Byte *) malloc(8);
    
    for (int i = 0; i < 8; ++i)
    {
        ret[i] = (Byte) (number & 255);
        number >>= 8;
    }
    
    return ret;
}

+ (Byte *) convertUnsigned2Bytes:(unsigned) number
{                                       //无符号整型转字节数组
    Byte * ret = (Byte *) malloc(4);
    
    for (int i = 0; i < 4; ++i)
    {
        ret[i] = (Byte) (number & 255);
        number >>= 8;
    }
    
    return ret;
}

+ (Byte *) convertIPString2Bytes:(NSString *) string
{                                       //IP地址转字节数组，先转换成无符号整型
    unsigned addr = inet_addr([string cStringUsingEncoding:NSUTF8StringEncoding]);
    return [WITUtils convertUnsigned2Bytes:addr];
}

+ (unsigned) convertBytes2Unsigned:(Byte *) bytes
{                                       //字节数组转无符号整型
    unsigned ret = 0;
    
    for (int i = 3; i >= 0; --i)
    {
        unsigned num = bytes[i];
        
        ret <<= 8;
        ret += num;
    }
    
    return ret;
}

+ (unsigned long) convertBytes2UnsignedLong:(Byte *) bytes
{                                       //字节数组转无符号长整型
    unsigned long ret = 0;
    
    for (int i = 7; i >= 0; --i)
    {
        unsigned long num = bytes[i];
        
        ret <<= 8;
        ret += num;
    }
        
    return ret;
}

+ (NSString *) convertBytes2String:(Byte *) bytes
{                                       //字节数组转字符串
    NSString *ret = [NSString stringWithUTF8String:(const char *) bytes];
    return ret;
}

+ (NSString *) convertBytes2IPString:(Byte *) bytes
{                                       //字节数组转IP地址，首先转无符号整型
    struct in_addr ina;
    
    ina.s_addr = [self convertBytes2Unsigned:bytes];
    char *tmp = inet_ntoa(ina);
    
    NSString *ret = [WITUtils convertBytes2String:(Byte *) tmp];
    return ret;
}

+ (NSString *) convertUnsignedLong2SizeString:(unsigned long) number
{                                       //无符号长整型转为带单位字符串
    NSArray *array = [NSArray arrayWithObjects:@"Byte", @"KB", @"MB", @"GB", nil];
    double decimal = (double) number;
    
    unsigned i;
    for (i = 0; i < [array count] - 1; ++i)
    {
        if (decimal < 1024) break;
        decimal /= 1024;
    }
    
    return [NSString stringWithFormat:@"%.1lf%@", decimal, [array objectAtIndex:i]];
}

@end
