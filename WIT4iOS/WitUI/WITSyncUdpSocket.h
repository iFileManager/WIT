//
//  SyncUdpSocket.h
//  
//  This class is in the public domain.
//  Originally created by Robbie Hanson on Wed Oct 01 2008.
//  Converted to a synchronized version by Aqua on Wed Jul 03 2013
//
//  http://code.google.com/p/cocoaAsyncsocket/
//

#import <Foundation/Foundation.h>

extern NSString *const SyncUdpSocketException;
extern NSString *const SyncUdpSocketErrorDomain;

enum SyncUdpSocketError
{
	SyncUdpSocketCFSocketError = kCFSocketError,	// From CFSocketError enum
	SyncUdpSocketNoError = 0,                      // Never used
	SyncUdpSocketBadParameter,                     // Used if given a bad parameter (such as an improper address)
	SyncUdpSocketIPv4Unavailable,                  // Used if you bind/connect using IPv6 only
	SyncUdpSocketIPv6Unavailable,                  // Used if you bind/connect using IPv4 only (or iPhone)
	SyncUdpSocketSendTimeoutError,
	SyncUdpSocketReceiveTimeoutError
};
typedef enum SyncUdpSocketError SyncUdpSocketError;

@interface WITSyncUdpSocket : NSObject
{
	CFSocketRef theSocket4;            // IPv4 socket
	
	CFRunLoopSourceRef theSource4;     // For theSocket4
	CFSocketContext theContext;
	
	id theDelegate;
	UInt16 theFlags;
	
	long theUserData;
	
	NSString *cachedLocalHost;
	UInt16 cachedLocalPort;
	
	UInt32 maxReceiveBufferSize;
}

/**
 * Creates new instances of SyncUdpSocket.
**/
- (id)init;
- (id)initWithDelegate:(id)delegate;
- (id)initWithDelegate:(id)delegate userData:(long)userData;

- (id)delegate;
- (void)setDelegate:(id)delegate;

- (long)userData;
- (void)setUserData:(long)userData;

/**
 * Returns the local address info for the socket.
 * 
 * Note: Address info may not be available until after the socket has been bind'ed,
 * or until after data has been sent.
**/
- (NSString *)localHost;
- (UInt16)localPort;

/**
 * Returns whether or not this socket has been closed.
 * The only way a socket can be closed is if you explicitly call one of the close methods.
**/
- (BOOL)isClosed;

/**
 * Returns whether or not this socket supports IPv4.
 * By default this will be true, unless the socket is specifically initialized as IPv6 only,
 * or is binded or connected to an IPv6 address.
**/
- (BOOL)isIPv4;

/**
 * Returns the mtu of the socket.
 * If unknown, returns zero.
 * 
 * Sending data larger than this may result in an error.
 * This is an advanced topic, and one should understand the wide range of mtu's on networks and the internet.
 * Therefore this method is only for reference and may be of little use in many situations.
**/
- (unsigned int)maximumTransmissionUnit;

/**
 * Binds the UDP socket to the given port and optional address.
 * Binding should be done for server sockets that receive data prior to sending it.
 * Client sockets can skip binding,
 * as the OS will automatically assign the socket an available port when it starts sending data.
 * 
 * You cannot bind a socket after its been connected.
 * You can only bind a socket once.
 * You can still connect a socket (if desired) after binding.
 * 
 * On success, returns YES.
 * Otherwise returns NO, and sets errPtr. If you don't care about the error, you can pass nil for errPtr.
**/
- (BOOL)bindToPort:(UInt16)port error:(NSError **)errPtr;
- (BOOL)bindToAddress:(NSString *)localAddr port:(UInt16)port error:(NSError **)errPtr;

/**
 * By default, the underlying socket in the OS will not allow you to send broadcast messages.
 * In order to send broadcast messages, you need to enable this functionality in the socket.
 * 
 * A broadcast is a UDP message to addresses like "192.168.255.255" or "255.255.255.255" that is
 * delivered to every host on the network.
 * The reason this is generally disabled by default is to prevent
 * accidental broadcast messages from flooding the network.
**/
- (BOOL)enableBroadcast:(BOOL)flag error:(NSError **)errPtr;

/**
 * Closes the socket immediately. Any pending send or receive operations are dropped.
**/
- (void)close;

- (void) doSyncSend:(NSData *) data toHost:(NSString *) host port:(UInt16) port;
- (NSData *) doSyncReceive;

@end
