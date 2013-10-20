//
//  WITMasterViewController.m
//  WitUI
//
//  Created by Aqua on 13-7-8.
//  Copyright (c) 2013年 __MyCompanyName__. All rights reserved.
//

#import "WITMasterViewController.h"

#import "WITDetailViewController.h"
#import "WITConstants.h"
#import "WITInFile.h"
#import "WITOutFile.h"
#import "WITService.h"
#import "WITUser.h"

#import <objc/runtime.h>
#import <AssetsLibrary/AssetsLibrary.h>

static char associatedKey;

@interface WITMasterViewController ()                           //用户列表面板
{
    NSMutableArray *_objects;                                   //用户数组
    WITService *service;                                        //底层服务
}
@end

@implementation WITMasterViewController

- (void)awakeFromNib
{
    [super awakeFromNib];
}

- (void)viewDidLoad
{
    [super viewDidLoad];
    
    service = [WITService mainService];                         //获取服务
    
    UIBarButtonItem *editNameButton = [[UIBarButtonItem alloc] initWithTitle:@"更改名称"
                                                                       style:UIBarButtonItemStyleBordered
                                                                      target:self
                                                                      action:@selector(editName)];
    
    self.navigationItem.title = service.myname;                 //设置标题为自己名称
    self.navigationItem.rightBarButtonItem = editNameButton;    //右边为编辑名称按钮
    
    NSNotificationCenter *center = [NSNotificationCenter defaultCenter];
    
    [center addObserver:self                                    //注册各通知监听
               selector:@selector(onUserlistChange)
                   name:NOTIFY_USERLIST_CHANGE
                 object:nil];
    
    [center addObserver:self
               selector:@selector(onReceiveRequest:)
                   name:NOTIFY_RECEIVE_REQUEST
                 object:nil];
    
    [center addObserver:self
               selector:@selector(onRemoteAcceptRequest)
                   name:NOTIFY_REMOTE_ACCEPT_REQUEST
                 object:nil];
    
    [center addObserver:self
               selector:@selector(onRemoteRefuseRequest)
                   name:NOTIFY_REMOTE_REFUSE_REQUEST
                 object:nil];
    
    [center addObserver:self
               selector:@selector(onDisconnect)
                   name:NOTIFY_DISCONNECT
                 object:nil];
    
    [center addObserver:self
               selector:@selector(onTransSuccess:)
                   name:NOTIFY_TRANS_SUCCESS
                 object:nil];
    
    [center addObserver:self
               selector:@selector(onTransFailed:)
                   name:NOTIFY_TRANS_FAILED
                 object:nil];
}

- (void)viewDidUnload                                           //取消加载
{
    [super viewDidUnload];
}

- (BOOL)shouldAutorotateToInterfaceOrientation:(UIInterfaceOrientation)interfaceOrientation
{
    return (interfaceOrientation != UIInterfaceOrientationPortraitUpsideDown);
}

- (void)editName                                                //编辑我的名字
{
    UIAlertView *alertView = [[UIAlertView alloc] initWithTitle:@"请输入用户名"
                                                    message:nil
                                                   delegate:self
                                          cancelButtonTitle:@"确定"
                                          otherButtonTitles:nil];
    
    alertView.alertViewStyle = UIAlertViewStylePlainTextInput;
    [alertView show];                                           //打开对话框
}

-(void)willPresentAlertView:(UIAlertView *)alertView            //对话框文本初值为当前名称
{
    [[alertView textFieldAtIndex:0] setText:service.myname];
}

-(void)alertView:(UIAlertView *)alertView didDismissWithButtonIndex:(NSInteger)buttonIndex
{                                                               //文本框设置完成
    service.myname = [[alertView textFieldAtIndex:0] text];     //设置名称，自动调用NSUserDefaults
    self.navigationItem.title = service.myname;                 //设置标题
}

#pragma mark - Table View

- (NSInteger)numberOfSectionsInTableView:(UITableView *)tableView
{
    return 1;
}

- (NSInteger)tableView:(UITableView *)tableView numberOfRowsInSection:(NSInteger)section
{
    return _objects.count;
}

- (UITableViewCell *)tableView:(UITableView *)tableView cellForRowAtIndexPath:(NSIndexPath *)indexPath
{                                                               //设置单元格
    static NSString* cellTableIdentifier = @"UserCell";
    UITableViewCell *cell = [tableView dequeueReusableCellWithIdentifier:cellTableIdentifier];
    
    if (!cell)                                                  //Cell不能为nil
    {
        cell = [[UITableViewCell alloc] initWithStyle:UITableViewCellStyleDefault
                                  reuseIdentifier:cellTableIdentifier];
    }
    
    WITUser *user = [_objects objectAtIndex:indexPath.row];
    NSString *label = [NSString stringWithString:user.username];
                                                                //用户状态
    if ([service isRequesting:user])
    {
        label = [label stringByAppendingString:@"(请求连接中)"];
    }
    
    if ([service isConnectedTo:user])
    {
        label = [label stringByAppendingString:@"(已连接)"];
    }
    
    cell.textLabel.text = label;                                //设置单元格标签
    return cell;
}

- (BOOL)tableView:(UITableView *)tableView canEditRowAtIndexPath:(NSIndexPath *)indexPath
{
    return NO;
}

- (void)prepareForSegue:(UIStoryboardSegue *)segue sender:(id)sender
{
    if ([[segue identifier] isEqualToString:@"showDetail"]) {
        NSIndexPath *indexPath = [self.tableView indexPathForSelectedRow];
        NSDate *object = [_objects objectAtIndex:indexPath.row];
        [[segue destinationViewController] setDetailItem:object];
    }
}

- (void)onUserlistChange                                        //用户列表更新
{
    dispatch_async(dispatch_get_main_queue(), ^{
        _objects = [service.userlist mutableCopy];              //主线程中更新列表
        [self.tableView reloadData];
    });
}

- (void)onReceiveRequest:(NSNotification *)notification         //接收用户请求
{
    WITUser *user = [[notification userInfo] objectForKey:@"user"];
    
    UIActionSheet *sheet = [[UIActionSheet alloc] initWithTitle:
                            [NSString stringWithFormat:@"%@请求连接", user.username]
                                                       delegate:self
                                              cancelButtonTitle:@"拒绝"
                                         destructiveButtonTitle:@"接受"
                                              otherButtonTitles:nil];
    
    sheet.tag = SHEET_RECEIVE_REQUEST;
    objc_setAssociatedObject(sheet, &associatedKey, user, OBJC_ASSOCIATION_RETAIN);
    
    dispatch_async(dispatch_get_main_queue(), ^{                //主线程中弹出请求Sheet
        [sheet showInView:self.navigationController.view];
    });
}

- (void) onRemoteAcceptRequest                                  //对方接收请求
{
    UIActionSheet *sheet = [[UIActionSheet alloc] initWithTitle:@"对方接受连接请求"
                                                       delegate:nil
                                              cancelButtonTitle:@"确定"
                                         destructiveButtonTitle:nil
                                              otherButtonTitles:nil];
    dispatch_async(dispatch_get_main_queue(), ^{
        [sheet showInView:self.navigationController.view];
    });
    
    [self onUserlistChange];
}

- (void) onRemoteRefuseRequest                                  //对方拒绝请求
{
    UIActionSheet *sheet = [[UIActionSheet alloc] initWithTitle:@"对方拒绝连接请求"
                                                       delegate:nil
                                              cancelButtonTitle:@"确定"
                                         destructiveButtonTitle:nil
                                              otherButtonTitles:nil];
    dispatch_async(dispatch_get_main_queue(), ^{
        [sheet showInView:self.navigationController.view];
    });
    
    [self onUserlistChange];
}

- (void) onDisconnect                                           //对方或自己断开
{
    UIActionSheet *sheet = [[UIActionSheet alloc] initWithTitle:@"连接已断开"
                                                       delegate:nil
                                              cancelButtonTitle:@"确定"
                                         destructiveButtonTitle:nil
                                              otherButtonTitles:nil];
    dispatch_async(dispatch_get_main_queue(), ^{
        [sheet showInView:self.navigationController.view];
    });
    
    [self onUserlistChange];
}

- (void) onTransSuccess:(NSNotification *) notification         //文件传输成功
{
    WITFile *file = [[notification userInfo] objectForKey:@"file"];
    BOOL isReceive = [file isKindOfClass:[WITOutFile class]];   //判断是接收还是发送
    
    NSString *transType = isReceive ? @"接收" : @"发送";    
    NSString *fileType = @"文件";
    
    switch (file.type)                                          //判断显示的文件类型
    {
        case TYPE_PHOTO:
            fileType = @"图片";
            break;
        case TYPE_CONTACT:
            fileType = @"联系人";
            break;
        default:
            break;
    }
    
    NSString *msg = [NSString stringWithFormat:@"%@ %@ %@成功", fileType, file.filename, transType];
    
    id delegate = nil;
    NSString *anotherButton = nil;
    
    if (isReceive)
    {
        switch (file.type)
        {
            case TYPE_PHOTO:
                delegate = self;
                anotherButton = @"打开照片库";
                
                {                                               //将收到的图片加入照片库
                    UIImage *image = [[UIImage alloc] initWithContentsOfFile:file.filepath];
                    ALAssetsLibrary *assetsLibrary = [[ALAssetsLibrary alloc] init];
                    [assetsLibrary writeImageToSavedPhotosAlbum:[image CGImage]
                                                    orientation:(ALAssetOrientation)image.imageOrientation
                                                completionBlock:nil];
                }
                
                break;
            case TYPE_CONTACT:
                delegate = self;
                anotherButton = @"打开联系人";
                
                {                                               //将收到的联系人加入联系人列表
                    ABAddressBookRef book = ABAddressBookCreate();
                    ABRecordRef defaultSource = ABAddressBookCopyDefaultSource(book);
                    
                    NSData *vCardData = [NSData dataWithContentsOfFile:file.filepath];
                    CFArrayRef vCardPeople = ABPersonCreatePeopleInSourceWithVCardRepresentation(defaultSource,CFBridgingRetain(vCardData));
                    
                    CFIndex count = CFArrayGetCount(vCardPeople);
                    for (CFIndex index = 0; index < count; ++index)
                    {
                        ABRecordRef person = CFArrayGetValueAtIndex(vCardPeople, index);
                        ABAddressBookAddRecord(book, person, NULL);
                    }
                    
                    ABAddressBookSave(book, NULL);
                }
                
                break;
            default:
                break;
        }
    }
    
    UIActionSheet *sheet = [[UIActionSheet alloc] initWithTitle:msg
                                                       delegate:delegate
                                              cancelButtonTitle:@"确定"
                                         destructiveButtonTitle:anotherButton
                                              otherButtonTitles:nil];
    sheet.tag = file.type;
    
    dispatch_async(dispatch_get_main_queue(), ^{                //主线程中弹出提示
        [sheet showInView:self.navigationController.view];
    });
}

- (void) onTransFailed:(NSNotification *) notification          //传输失败
{
    WITFile *file = [[notification userInfo] objectForKey:@"file"];
    
    NSString *transType = [file isKindOfClass:[WITInFile class]] ? @"发送" : @"接收";
    NSString *fileType = @"文件";
    
    switch (file.type)
    {
        case TYPE_PHOTO:
            fileType = @"图片";
            break;
        case TYPE_CONTACT:
            fileType = @"联系人";
            break;
        default:
            break;
    }
    
    NSString *msg = [NSString stringWithFormat:@"%@ %@ %@失败", fileType, file.filename, transType];
    
    UIActionSheet *sheet = [[UIActionSheet alloc] initWithTitle:msg
                                                       delegate:nil
                                              cancelButtonTitle:@"确定"
                                         destructiveButtonTitle:nil
                                              otherButtonTitles:nil];
    
    dispatch_async(dispatch_get_main_queue(), ^{                //主线程中弹出提示
        [sheet showInView:self.navigationController.view];
    });
}

- (void)actionSheet:(UIActionSheet *)actionSheet didDismissWithButtonIndex:(NSInteger)buttonIndex
{
    WITUser *user;
    
    switch (actionSheet.tag)                                    //根据Sheet的Tag
    {
        case SHEET_RECEIVE_REQUEST:
            user = (WITUser *) objc_getAssociatedObject(actionSheet, &associatedKey);
            
            if (buttonIndex == 0)
                [service acceptConnectRequest:user];            //接受连接请求
            else
                [service refuseConnectRequest:user];            //拒绝连接请求
            
            break;
        case TYPE_PHOTO:
            if (buttonIndex == 0)
            {
                UIImagePickerController *picker = [[UIImagePickerController alloc] init];
                
                picker.delegate = nil;
                picker.sourceType = UIImagePickerControllerSourceTypePhotoLibrary;
                picker.allowsEditing = YES;
                
                [self presentViewController:picker animated:YES completion:nil];
            }                                                   //打开照片库
            
            break;
        case TYPE_CONTACT:
            if (buttonIndex == 0)
            {
                ABPeoplePickerNavigationController *picker = [[ABPeoplePickerNavigationController alloc] init];
                
                picker.peoplePickerDelegate = self;
                
                [self presentViewController:picker animated:YES completion:nil];
            }                                                   //打开联系人列表
            
            break;
        default:
            break;
    }    
}

- (void)peoplePickerNavigationControllerDidCancel: 
(ABPeoplePickerNavigationController *)peoplePicker 
{ 
    [self dismissViewControllerAnimated:YES completion:nil];
} 


- (BOOL)peoplePickerNavigationController:(ABPeoplePickerNavigationController *)peoplePicker 
      shouldContinueAfterSelectingPerson:(ABRecordRef)person
{ 
    return YES; 
} 

- (BOOL)peoplePickerNavigationController:(ABPeoplePickerNavigationController *)peoplePicker 
      shouldContinueAfterSelectingPerson:(ABRecordRef)person 
                                property:(ABPropertyID)property 
                              identifier:(ABMultiValueIdentifier)identifier 
{ 
    return YES; 
}

@end
