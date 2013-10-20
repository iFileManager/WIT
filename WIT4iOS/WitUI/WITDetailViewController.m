//
//  WITDetailViewController.m
//  WitUI
//
//  Created by Aqua on 13-7-8.
//  Copyright (c) 2013年 __MyCompanyName__. All rights reserved.
//

#import "WITDetailViewController.h"
#import "WITConstants.h"
#import "WITFile.h"
#import "WITFileCell.h"
#import "WITService.h"
#import "WITUser.h"

#import <AddressBook/AddressBook.h>
#import <AssetsLibrary/AssetsLibrary.h>

@interface WITDetailViewController ()               //单个用户面板
{
    WITService *service;                            //底层服务
    WITUser *user;                                  //对应用户
}

- (void)configureView;
@end

@implementation WITDetailViewController

@synthesize detailItem = _detailItem;               //各属性之实现
@synthesize lblStatus = _lblStatus;
@synthesize btnConnect = _btnConnect;
@synthesize btnPhoto = _btnPhoto;
@synthesize btnContact = _btnContact;
@synthesize tableView = _tableView;
@synthesize objects = _objects;

#pragma mark - Managing the detail item

- (void)setDetailItem:(id)newDetailItem
{
    if (_detailItem != newDetailItem) {
        _detailItem = newDetailItem;
        
        user = (WITUser *) _detailItem;
        
        // Update the view.
        [self configureView];
    }
}

- (void)configureView                               //配置窗体
{
    // Update the user interface for the detail item.

    if (self.detailItem) {
        self.navigationItem.title = user.username;  //用户名为标题
        
        if (![service.userlist containsObject:user])//用户已下线
        {
            [self.btnConnect setTitle:@"请求连接" forState:UIControlStateNormal];
            [self.lblStatus setText:@"已下线"];
            self.btnConnect.enabled = NO;
        }
        else if ([service isRequesting:user])       //正在请求该用户
        {
            [self.btnConnect setTitle:@"撤回请求" forState:UIControlStateNormal];
            [self.lblStatus setText:@"请求中"];
            self.btnConnect.enabled = YES;
        }
        else if ([service isConnectedTo:user])      //已连接到该用户
        {
            [self.btnConnect setTitle:@"断开连接" forState:UIControlStateNormal];
            [self.lblStatus setText:@" 已连接"];
            self.btnConnect.enabled = YES;
        }
        else
        {
            [self.btnConnect setTitle:@"请求连接" forState:UIControlStateNormal];
            
            if (![service isConnected])             //本机未连接
            {
                self.btnConnect.enabled = YES;
                [self.lblStatus setText:@"空闲"];
            }
            else                                    //本机已连接
            {
                self.btnConnect.enabled = NO;
                [self.lblStatus setText:@"本机已连接到其他用户"];
            }
        }
        
        self.btnPhoto.enabled = [service isConnectedTo:user];
        self.btnContact.enabled = [service isConnectedTo:user];
                                                    //对连接的用户，启用发送图片、通讯录按钮
        _objects = [service isConnectedTo:user] ? [service.filelist  mutableCopy] : [[NSMutableArray alloc] init];
                                                    //只有连上的用户才有传输列表
        [self.tableView reloadData];                //刷新传输列表
    }
}

- (void)viewDidLoad                                 //窗体加载成功
{
    [super viewDidLoad];
    
	service = [WITService mainService];             //获取底层服务
    
    NSNotificationCenter *center = [NSNotificationCenter defaultCenter];
    
    [center addObserver:self                        //注册各通知
               selector:@selector(onNotify)
                   name:NOTIFY_USERLIST_CHANGE
                 object:nil];
    
    [center addObserver:self
               selector:@selector(onNotify)
                   name:NOTIFY_REMOTE_ACCEPT_REQUEST
                 object:nil];
    
    [center addObserver:self
               selector:@selector(onNotify)
                   name:NOTIFY_REMOTE_REFUSE_REQUEST
                 object:nil];
    
    [center addObserver:self
               selector:@selector(onNotify)
                   name:NOTIFY_DISCONNECT
                 object:nil];
    
    [center addObserver:self
               selector:@selector(onNotify)
                   name:NOTIFY_FILELIST_CHANGE
                 object:nil];
    
    [self configureView];
}

- (void)viewDidUnload
{
    [self setBtnConnect:nil];
    [self setBtnPhoto:nil];
    [self setBtnContact:nil];
    [self setLblStatus:nil];
    [self setTableView:nil];
    [super viewDidUnload];
}

- (BOOL)shouldAutorotateToInterfaceOrientation:(UIInterfaceOrientation)interfaceOrientation
{
    return (interfaceOrientation != UIInterfaceOrientationPortraitUpsideDown);
}

- (NSInteger)numberOfSectionsInTableView:(UITableView *)tableView
{
    return 1;
}

- (NSInteger)tableView:(UITableView *)tableView numberOfRowsInSection:(NSInteger)section
{
    return _objects.count;
}

- (UITableViewCell *)tableView:(UITableView *)tableView cellForRowAtIndexPath:(NSIndexPath *)indexPath
{                                                   //显示自定义的单元格
    static NSString *cellTableIdentifier = @"FileCell";
    
    static BOOL nibsRegistered = NO;                //载入NIB文件
    if (!nibsRegistered) {
        UINib *nib = [UINib nibWithNibName:@"WITFileCell" bundle:nil];
        [tableView registerNib:nib forCellReuseIdentifier:cellTableIdentifier];
        nibsRegistered = YES;
    }
    
    WITFileCell *cell = [tableView dequeueReusableCellWithIdentifier:
                                 cellTableIdentifier];
    
    if (!cell)                                      //单元格不能为nil
    {
        cell = [[WITFileCell alloc] initWithStyle:UITableViewCellStyleDefault
                                      reuseIdentifier:cellTableIdentifier];
    }
    
    NSUInteger row = [indexPath row];
    WITFile *file = [_objects objectAtIndex:row];
    [cell applyFile:file];                          //通过文件设置单元格
    [cell setSelectionStyle:UITableViewCellSelectionStyleNone];
    
    return cell;
}

- (BOOL)tableView:(UITableView *)tableView canEditRowAtIndexPath:(NSIndexPath *)indexPath
{
    return NO;
}

- (IBAction)btnConnect_click:(id)sender             //点击连接按钮，根据其文本不同进行不同行为
{
    NSString *buttonText = self.btnConnect.titleLabel.text;
    
    if ([buttonText isEqualToString:@"请求连接"])
    {
        [service sendConnectRequest:user];          //连接到用户
    }
    else if ([buttonText isEqualToString:@"撤回请求"])
    {
        [service cancelConnectRequest];             //撤回连接
    }
    else if ([buttonText isEqualToString:@"断开连接"])
    {
        [service sendDisconnectRequest];            //断开连接
    }
    
    [self configureView];                           //刷新窗体
}

- (void)onNotify                                    //收到通知
{
    dispatch_async(dispatch_get_main_queue(), ^{    //在主线程中刷新窗体
        [self configureView];
    });
}

- (IBAction)btnPhoto_click:(id)sender               //点击发送图片按钮
{
    UIImagePickerController * picker = [[UIImagePickerController alloc] init];
	
    picker.delegate = self;
    picker.sourceType = UIImagePickerControllerSourceTypePhotoLibrary;
    
    [self presentViewController:picker animated:YES completion:nil];
}                                                   //显示图片选取界面

- (void)imagePickerController:(UIImagePickerController *)picker
didFinishPickingMediaWithInfo:(NSDictionary *)info  //选取了图片
{
    [self dismissViewControllerAnimated:YES completion:nil];
    UIImage *image = [info objectForKey:UIImagePickerControllerOriginalImage];
    
    NSString* name = [NSString stringWithFormat:@"%@_%lu", service.myname, (unsigned long)[[[NSDate alloc] init] timeIntervalSince1970]];
                                                    //以用户名_时间戳为文件名
    NSString *filename = [name stringByAppendingPathExtension:@"png"];
                                                    //统一为PNG格式
    NSString *filepath = [service.sendDirectory stringByAppendingPathComponent:filename];
    
    NSData *data = [NSData dataWithData:UIImagePNGRepresentation(image)];
    [data writeToFile:filepath atomically:YES];     //写入到沙盒内
    
    [service sendFile:filepath ofType:TYPE_PHOTO];  //发送
}

- (void)imagePickerControllerDidCancel:(UIImagePickerController *)picker
{
    [self dismissViewControllerAnimated:YES completion:nil];
}

- (IBAction)btnContact_click:(id)sender             //点击发送联系人按钮
{
    ABPeoplePickerNavigationController *picker = [[ABPeoplePickerNavigationController alloc] init];
    
    picker.peoplePickerDelegate = self;
    
    [self presentViewController:picker animated:YES completion:nil];
}                                                   //显示联系人选取界面

- (void)peoplePickerNavigationControllerDidCancel: 
(ABPeoplePickerNavigationController *)peoplePicker 
{ 
    [self dismissViewControllerAnimated:YES completion:nil];
} 


- (BOOL)peoplePickerNavigationController:(ABPeoplePickerNavigationController *)peoplePicker 
      shouldContinueAfterSelectingPerson:(ABRecordRef)person
{                                                   //选中联系人
    [self dismissViewControllerAnimated:YES completion:nil];
    
    ABRecordRef people[1];
    people[0] = person;
    
    CFArrayRef peopleArray = CFArrayCreate(NULL, (void *)people, 1, &kCFTypeArrayCallBacks);
    
    NSData *data = CFBridgingRelease(ABPersonCreateVCardRepresentationWithPeople(peopleArray));
                                                    //转为VCF格式
    NSString *firstName = CFBridgingRelease(ABRecordCopyValue(person, kABPersonFirstNameProperty));
    if (!firstName) firstName = @"";
    
    NSString *lastName = CFBridgingRelease(ABRecordCopyValue(person, kABPersonLastNameProperty));
    if (!lastName) lastName = @"";
    
    NSString *name = (ABPersonGetCompositeNameFormat() == kABPersonCompositeNameFormatFirstNameFirst) ?
            [NSString stringWithFormat:@"%@ %@", firstName, lastName] :
            [NSString stringWithFormat:@"%@ %@", lastName, firstName];
    
    NSString *filename = [name stringByAppendingPathExtension:@"vcf"];
                                                    //以姓名作为文件名
    NSString *filepath = [service.sendDirectory stringByAppendingPathComponent:filename];
    [data writeToFile:filepath atomically:YES];     //写入沙盒内
    
    [service sendFile:filepath ofType:TYPE_CONTACT];//发送
    
    return NO; 
} 

- (BOOL)peoplePickerNavigationController:(ABPeoplePickerNavigationController *)peoplePicker 
      shouldContinueAfterSelectingPerson:(ABRecordRef)person 
                                property:(ABPropertyID)property 
                              identifier:(ABMultiValueIdentifier)identifier 
{ 
    return NO; 
} 


@end
