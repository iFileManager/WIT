//
//  WITFileCell.m
//  WitUI
//
//  Created by Aqua on 13-7-8.
//  Copyright (c) 2013年 __MyCompanyName__. All rights reserved.
//

#import "WITFileCell.h"
#import "WITUtils.h"

@implementation WITFileCell

@synthesize lblMethod;                                          //实现各方法
@synthesize lblPath;
@synthesize progress;
@synthesize lblSize;

- (id)initWithStyle:(UITableViewCellStyle)style reuseIdentifier:(NSString *)reuseIdentifier
{
    self = [super initWithStyle:style reuseIdentifier:reuseIdentifier];
    return self;
}

- (void)setSelected:(BOOL)selected animated:(BOOL)animated
{
    [super setSelected:selected animated:animated];
}

- (void) applyFile:(WITFile *) file                            //根据文件设置属性
{
    [lblMethod setText:([file isKindOfClass:[WITInFile class]] ? @"发送" : @"接收")];
    [lblPath setText:file.filename];
    [progress setProgress:(float) file.percent];
    
    if (![file isCompleted])
        [lblSize setText:[NSString stringWithFormat:@"%@/%@",
                          [WITUtils convertUnsignedLong2SizeString:file.completedSize],
                          [WITUtils convertUnsignedLong2SizeString:file.size]]];
    else
        [lblSize setText:@"传输完成"];
}

@end
