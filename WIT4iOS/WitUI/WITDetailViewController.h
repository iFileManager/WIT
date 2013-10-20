//
//  WITDetailViewController.h
//  WitUI
//
//  Created by Aqua on 13-7-8.
//  Copyright (c) 2013年 __MyCompanyName__. All rights reserved.
//

#import <UIKit/UIKit.h>
#import <AddressBookUI/AddressBookUI.h>

@interface WITDetailViewController : UIViewController <UINavigationControllerDelegate, UIImagePickerControllerDelegate, ABPeoplePickerNavigationControllerDelegate>

@property (strong, nonatomic) id detailItem;

@property (strong, nonatomic) IBOutlet UILabel *lblStatus;          //对方用户状态
@property (strong, nonatomic) IBOutlet UIButton *btnConnect;        //连接/断开/撤回连接按钮
@property (strong, nonatomic) IBOutlet UIButton *btnPhoto;          //发送图片按钮
@property (strong, nonatomic) IBOutlet UIButton *btnContact;        //发送联系人按钮
@property (strong, nonatomic) IBOutlet UITableView *tableView;      //传输列表
@property (strong, nonatomic) NSMutableArray *objects;              //传输的文件

- (IBAction)btnConnect_click:(id)sender;                            //点击连接/断开/撤回连接按钮
- (IBAction)btnPhoto_click:(id)sender;                              //点击发送图片按钮
- (IBAction)btnContact_click:(id)sender;                            //点击发送联系人按钮

@end
