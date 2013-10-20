//
//  WITUtils.h
//  Wit
//
//  Created by Aqua on 13-7-1.
//  Copyright (c) 2013年 __MyCompanyName__. All rights reserved.
//

#import <Foundation/Foundation.h>

@interface WITUtils : NSObject                          //其他操作，提供各种转换

+ (Byte *) convertString2Bytes:(NSString *) string withMaxLength:(unsigned) len;
+ (Byte *) convertIPString2Bytes:(NSString *) string;

+ (Byte *) convertUnsignedLong2Bytes:(unsigned long) number;
+ (Byte *) convertUnsigned2Bytes:(unsigned) number;

+ (unsigned) convertBytes2Unsigned:(Byte *) bytes;
+ (unsigned long) convertBytes2UnsignedLong:(Byte *) bytes;

+ (NSString *) convertBytes2String:(Byte *) bytes;
+ (NSString *) convertBytes2IPString:(Byte *) bytes;

+ (NSString *) convertUnsignedLong2SizeString:(unsigned long) number;

@end
