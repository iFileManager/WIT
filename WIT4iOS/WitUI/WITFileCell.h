//
//  WITFileCell.h
//  WitUI
//
//  Created by Aqua on 13-7-8.
//  Copyright (c) 2013年 __MyCompanyName__. All rights reserved.
//

#import <UIKit/UIKit.h>
#import "WITInFile.h"
#import "WITOutFile.h"

@interface WITFileCell : UITableViewCell                        //传输文件单元格

@property (strong, nonatomic) IBOutlet UILabel *lblMethod;      //发送/接收
@property (strong, nonatomic) IBOutlet UILabel *lblPath;        //文件名
@property (strong, nonatomic) IBOutlet UIProgressView *progress;//进度
@property (strong, nonatomic) IBOutlet UILabel *lblSize;        //文件已传输/总大小

- (void) applyFile:(WITFile *) file;                            //根据文件设置属性

@end
