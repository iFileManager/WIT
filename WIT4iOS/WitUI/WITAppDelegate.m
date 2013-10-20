//
//  WITAppDelegate.m
//  WitUI
//
//  Created by Aqua on 13-7-8.
//  Copyright (c) 2013年 __MyCompanyName__. All rights reserved.
//

#import "WITAppDelegate.h"
#import "WITService.h"

@interface WITAppDelegate ()
{
    WITService *service;                //底层服务
}

@end

@implementation WITAppDelegate

@synthesize window = _window;

- (BOOL)application:(UIApplication *)application didFinishLaunchingWithOptions:(NSDictionary *)launchOptions
{
    service = [WITService mainService]; //获取（启动）服务
    return YES;
}

- (void)applicationWillEnterForeground:(UIApplication *)application
{
    [service refreshIP];                //重新进入窗口时刷新IP
}

- (void)applicationWillTerminate:(UIApplication *)application
{
    [service close];                    //退出时关闭服务
}

@end
