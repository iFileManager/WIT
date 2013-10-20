//
//  WITConstants.c
//  Wit
//
//  Created by Aqua on 13-7-2.
//  Copyright (c) 2013年 __MyCompanyName__. All rights reserved.
//

#import "WITConstants.h"

const char HEADER[3] = { 'W', 'I', 'T' };                   //保留文件头

NSString *WITNAME = @"Wit";                                 //默认名称

/* 通知名 */
NSString* NOTIFY_USERLIST_CHANGE = @"WitUserlistChange";
NSString* NOTIFY_FILELIST_CHANGE = @"WitFilelistChange";

NSString *NOTIFY_RECEIVE_REQUEST = @"WitReceiveRequest";
NSString *NOTIFY_REMOTE_ACCEPT_REQUEST = @"WitRemoteAcceptRequest";
NSString *NOTIFY_REMOTE_REFUSE_REQUEST = @"WitRemoteRefuseRequest";
NSString *NOTIFY_DISCONNECT = @"WitDisconnect";

NSString *NOTIFY_MSG = @"WitMessage";

NSString *NOTIFY_TRANS_SUCCESS = @"WitTransSuccess";
NSString *NOTIFY_TRANS_FAILED = @"WitTransFailed";

/* 默认设置名 */
NSString *DEFAULT_USERNAME = @"WitUsername";
NSString *DEFAULT_USERID = @"WitUserId";

/* 默认目录 */
NSString *SEND_DIRECTORY = @"send";
NSString *RECV_DIRECTORY = @"receive";