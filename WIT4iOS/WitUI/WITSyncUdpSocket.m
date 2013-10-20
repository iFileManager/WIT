//
//  SyncUdpSocket.h
//  
//  This class is in the public domain.
//  Originally created by Robbie Hanson on Wed Oct 01 2008.
//  Converted to a synchronized version by Aqua on Wed Jul 03 2013
//
//  http://code.google.com/p/cocoaAsyncsocket/
//

#if ! __has_feature(objc_arc)
#warning This file must be compiled with ARC. Use -fobjc-arc flag (or convert project to ARC).
#endif

#import "WITSyncUdpSocket.h"
#import <sys/socket.h>
#import <netinet/in.h>
#import <arpa/inet.h>
#import <sys/ioctl.h>
#import <net/if.h>
#import <netdb.h>

#if TARGET_OS_IPHONE
// Note: You may need to add the CFNetwork Framework to your project
#import <CFNetwork/CFNetwork.h>
#endif


#define SENDQUEUE_CAPACITY	  5   // Initial capacity
#define RECEIVEQUEUE_CAPACITY 5   // Initial capacity

#define DEFAULT_MAX_RECEIVE_BUFFER_SIZE 9216

NSString *const SyncUdpSocketException = @"SyncUdpSocketException";
NSString *const SyncUdpSocketErrorDomain = @"SyncUdpSocketErrorDomain";

#if MAC_OS_X_VERSION_MIN_REQUIRED < MAC_OS_X_VERSION_10_5
// Mutex lock used by all instances of SyncUdpSocket, to protect getaddrinfo.
// Prior to Mac OS X 10.5 this method was not thread-safe.
static NSString *getaddrinfoLock = @"lock";
#endif

enum SyncUdpSocketFlags
{
	kDidBind                 = 1 <<  0,  // If set, bind has been called.
	kDidClose                = 1 <<  9,  // If set, the socket has been closed, and should not be used anymore.
};

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
#pragma mark -
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

@implementation WITSyncUdpSocket

- (id)initWithDelegate:(id)delegate userData:(long)userData enableIPv4:(BOOL)enableIPv4 enableIPv6:(BOOL)enableIPv6
{
	if((self = [super init]))
	{
		theFlags = 0;
		theDelegate = delegate;
		theUserData = userData;
		maxReceiveBufferSize = DEFAULT_MAX_RECEIVE_BUFFER_SIZE;
		
		// Socket context
		theContext.version = 0;
		theContext.info = (__bridge void *)self;
		theContext.retain = nil;
		theContext.release = nil;
		theContext.copyDescription = nil;
		
		// Create the sockets
		theSocket4 = NULL;
        
		if(enableIPv4)
		{
			theSocket4 = CFSocketCreate(kCFAllocatorDefault,
										PF_INET,
										SOCK_DGRAM,
										IPPROTO_UDP,
										kCFSocketReadCallBack | kCFSocketWriteCallBack,
										NULL,
										&theContext);
		}
				
		// Disable continuous callbacks for read and write.
		// If we don't do this, the socket(s) will just sit there firing read callbacks
		// at us hundreds of times a second if we don't immediately read the available data.
		if(theSocket4)
		{
			CFSocketSetSocketFlags(theSocket4, kCFSocketCloseOnInvalidate);
		}
				
		cachedLocalPort = 0;
	}
	return self;
}

- (id)init
{
	return [self initWithDelegate:nil userData:0 enableIPv4:YES enableIPv6:YES];
}

- (id)initWithDelegate:(id)delegate
{
	return [self initWithDelegate:delegate userData:0 enableIPv4:YES enableIPv6:YES];
}

- (id)initWithDelegate:(id)delegate userData:(long)userData
{
	return [self initWithDelegate:delegate userData:userData enableIPv4:YES enableIPv6:YES];
}

- (void) dealloc
{
	[self close];
	
	[NSObject cancelPreviousPerformRequestsWithTarget:theDelegate selector:@selector(onUdpSocketDidClose:) object:self];
	[NSObject cancelPreviousPerformRequestsWithTarget:self];
}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
#pragma mark Accessors
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

- (id)delegate
{
	return theDelegate;
}

- (void)setDelegate:(id)delegate
{
	theDelegate = delegate;
}

- (long)userData
{
	return theUserData;
}

- (void)setUserData:(long)userData
{
	theUserData = userData;
}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
#pragma mark Utilities:
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

/**
 * Attempts to convert the given host/port into and IPv4 and/or IPv6 data structure.
 * The data structure is of type sockaddr_in for IPv4 and sockaddr_in6 for IPv6.
 *
 * Returns zero on success, or one of the error codes listed in gai_strerror if an error occurs (as per getaddrinfo).
**/
- (int)convertForBindHost:(NSString *)host
					 port:(UInt16)port
			 intoAddress4:(NSData **)address4
				 address6:(NSData **)address6
{
	if(host == nil || ([host length] == 0))
	{
		// Use ANY address
		struct sockaddr_in nativeAddr;
		nativeAddr.sin_len         = sizeof(struct sockaddr_in);
		nativeAddr.sin_family      = AF_INET;
		nativeAddr.sin_port        = htons(port);
		nativeAddr.sin_addr.s_addr = htonl(INADDR_ANY);
		memset(&(nativeAddr.sin_zero), 0, sizeof(nativeAddr.sin_zero));
		
		struct sockaddr_in6 nativeAddr6;
		nativeAddr6.sin6_len       = sizeof(struct sockaddr_in6);
		nativeAddr6.sin6_family    = AF_INET6;
		nativeAddr6.sin6_port      = htons(port);
		nativeAddr6.sin6_flowinfo  = 0;
		nativeAddr6.sin6_addr      = in6addr_any;
		nativeAddr6.sin6_scope_id  = 0;
		
		// Wrap the native address structures for CFSocketSetAddress.
		if(address4) *address4 = [NSData dataWithBytes:&nativeAddr length:sizeof(nativeAddr)];
		if(address6) *address6 = [NSData dataWithBytes:&nativeAddr6 length:sizeof(nativeAddr6)];
		
		return 0;
	}
	else if([host isEqualToString:@"localhost"] || [host isEqualToString:@"loopback"])
	{
		// Note: getaddrinfo("localhost",...) fails on 10.5.3
		
		// Use LOOPBACK address
		struct sockaddr_in nativeAddr;
		nativeAddr.sin_len         = sizeof(struct sockaddr_in);
		nativeAddr.sin_family      = AF_INET;
		nativeAddr.sin_port        = htons(port);
		nativeAddr.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
		memset(&(nativeAddr.sin_zero), 0, sizeof(nativeAddr.sin_zero));
		
		struct sockaddr_in6 nativeAddr6;
		nativeAddr6.sin6_len       = sizeof(struct sockaddr_in6);
		nativeAddr6.sin6_family    = AF_INET6;
		nativeAddr6.sin6_port      = htons(port);
		nativeAddr6.sin6_flowinfo  = 0;
		nativeAddr6.sin6_addr      = in6addr_loopback;
		nativeAddr6.sin6_scope_id  = 0;
		
		// Wrap the native address structures for CFSocketSetAddress.
		if(address4) *address4 = [NSData dataWithBytes:&nativeAddr length:sizeof(nativeAddr)];
		if(address6) *address6 = [NSData dataWithBytes:&nativeAddr6 length:sizeof(nativeAddr6)];
		
		return 0;
	}
	else
	{
		NSString *portStr = [NSString stringWithFormat:@"%hu", port];
		
#if MAC_OS_X_VERSION_MIN_REQUIRED < MAC_OS_X_VERSION_10_5
		@synchronized (getaddrinfoLock)
#endif
		{
			struct addrinfo hints, *res, *res0;
			
			memset(&hints, 0, sizeof(hints));
			hints.ai_family   = PF_UNSPEC;
			hints.ai_socktype = SOCK_DGRAM;
			hints.ai_protocol = IPPROTO_UDP;
			hints.ai_flags    = AI_PASSIVE;
			
			int error = getaddrinfo([host UTF8String], [portStr UTF8String], &hints, &res0);
			
			if(error) return error;
			
			for(res = res0; res; res = res->ai_next)
			{
				if(address4 && !*address4 && (res->ai_family == AF_INET))
				{
					// Found IPv4 address
					// Wrap the native address structures for CFSocketSetAddress.
					if(address4) *address4 = [NSData dataWithBytes:res->ai_addr length:res->ai_addrlen];
				}
				else if(address6 && !*address6 && (res->ai_family == AF_INET6))
				{
					// Found IPv6 address
					// Wrap the native address structures for CFSocketSetAddress.
					if(address6) *address6 = [NSData dataWithBytes:res->ai_addr length:res->ai_addrlen];
				}
			}
			freeaddrinfo(res0);
		}
		
		return 0;
	}
}

/**
 * Attempts to convert the given host/port into and IPv4 and/or IPv6 data structure.
 * The data structure is of type sockaddr_in for IPv4 and sockaddr_in6 for IPv6.
 *
 * Returns zero on success, or one of the error codes listed in gai_strerror if an error occurs (as per getaddrinfo).
**/
- (int)convertForSendHost:(NSString *)host
					  port:(UInt16)port
			  intoAddress4:(NSData **)address4
				  address6:(NSData **)address6
{
	if(host == nil || ([host length] == 0))
	{
		// We're not binding, so what are we supposed to do with this?
		return EAI_NONAME;
	}
	else if([host isEqualToString:@"localhost"] || [host isEqualToString:@"loopback"])
	{
		// Note: getaddrinfo("localhost",...) fails on 10.5.3
		
		// Use LOOPBACK address
		struct sockaddr_in nativeAddr;
		nativeAddr.sin_len         = sizeof(struct sockaddr_in);
		nativeAddr.sin_family      = AF_INET;
		nativeAddr.sin_port        = htons(port);
		nativeAddr.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
		memset(&(nativeAddr.sin_zero), 0, sizeof(nativeAddr.sin_zero));
		
		struct sockaddr_in6 nativeAddr6;
		nativeAddr6.sin6_len       = sizeof(struct sockaddr_in6);
		nativeAddr6.sin6_family    = AF_INET6;
		nativeAddr6.sin6_port      = htons(port);
		nativeAddr6.sin6_flowinfo  = 0;
		nativeAddr6.sin6_addr      = in6addr_loopback;
		nativeAddr6.sin6_scope_id  = 0;
		
		// Wrap the native address structures for CFSocketSetAddress.
		if(address4) *address4 = [NSData dataWithBytes:&nativeAddr length:sizeof(nativeAddr)];
		if(address6) *address6 = [NSData dataWithBytes:&nativeAddr6 length:sizeof(nativeAddr6)];
		
		return 0;
	}
	else
	{
		NSString *portStr = [NSString stringWithFormat:@"%hu", port];

#if MAC_OS_X_VERSION_MIN_REQUIRED < MAC_OS_X_VERSION_10_5		
		@synchronized (getaddrinfoLock)
#endif
		{
			struct addrinfo hints, *res, *res0;
			
			memset(&hints, 0, sizeof(hints));
			hints.ai_family   = PF_UNSPEC;
			hints.ai_socktype = SOCK_DGRAM;
			hints.ai_protocol = IPPROTO_UDP;
			// No passive flag on a send or connect
			
			int error = getaddrinfo([host UTF8String], [portStr UTF8String], &hints, &res0);
			
			if(error) return error;
			
			for(res = res0; res; res = res->ai_next)
			{
				if(address4 && !*address4 && (res->ai_family == AF_INET))
				{
					// Found IPv4 address
					// Wrap the native address structures for CFSocketSetAddress.
					if(address4) *address4 = [NSData dataWithBytes:res->ai_addr length:res->ai_addrlen];
				}
				else if(address6 && !*address6 && (res->ai_family == AF_INET6))
				{
					// Found IPv6 address
					// Wrap the native address structures for CFSocketSetAddress.
					if(address6) *address6 = [NSData dataWithBytes:res->ai_addr length:res->ai_addrlen];
				}
			}
			freeaddrinfo(res0);
		}
		
		return 0;
	}
}

- (NSString *)addressHost4:(struct sockaddr_in *)pSockaddr4
{
	char addrBuf[INET_ADDRSTRLEN];
	
	if(inet_ntop(AF_INET, &pSockaddr4->sin_addr, addrBuf, sizeof(addrBuf)) == NULL)
	{
		[NSException raise:NSInternalInconsistencyException format:@"Cannot convert address to string."];
	}
	
	return [NSString stringWithCString:addrBuf encoding:NSASCIIStringEncoding];
}

- (NSString *)addressHost6:(struct sockaddr_in6 *)pSockaddr6
{
	char addrBuf[INET6_ADDRSTRLEN];
	
	if(inet_ntop(AF_INET6, &pSockaddr6->sin6_addr, addrBuf, sizeof(addrBuf)) == NULL)
	{
		[NSException raise:NSInternalInconsistencyException format:@"Cannot convert address to string."];
	}
	
	return [NSString stringWithCString:addrBuf encoding:NSASCIIStringEncoding];
}

- (NSString *)addressHost:(struct sockaddr *)pSockaddr
{
	if(pSockaddr->sa_family == AF_INET)
	{
		return [self addressHost4:(struct sockaddr_in *)pSockaddr];
	}
	else
	{
		return [self addressHost6:(struct sockaddr_in6 *)pSockaddr];
	}
}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
#pragma mark Socket Implementation:
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

/**
 * Binds the underlying socket(s) to the given port.
 * The socket(s) will be able to receive data on any interface.
 * 
 * On success, returns YES.
 * Otherwise returns NO, and sets errPtr. If you don't care about the error, you can pass nil for errPtr.
**/
- (BOOL)bindToPort:(UInt16)port error:(NSError **)errPtr
{
	return [self bindToAddress:nil port:port error:errPtr];
}

/**
 * Binds the underlying socket(s) to the given address and port.
 * The sockets(s) will be able to receive data only on the given interface.
 * 
 * To receive data on any interface, pass nil or "".
 * To receive data only on the loopback interface, pass "localhost" or "loopback".
 * 
 * On success, returns YES.
 * Otherwise returns NO, and sets errPtr. If you don't care about the error, you can pass nil for errPtr.
**/
- (BOOL)bindToAddress:(NSString *)host port:(UInt16)port error:(NSError **)errPtr
{
	if(theFlags & kDidClose)
	{
		[NSException raise:SyncUdpSocketException
		            format:@"The socket is closed."];
	}
	if(theFlags & kDidBind)
	{
		[NSException raise:SyncUdpSocketException
		            format:@"Cannot bind a socket more than once."];
	}
	
	// Convert the given host/port into native address structures for CFSocketSetAddress
	NSData *address4 = nil, *address6 = nil;
	
	int gai_error = [self convertForBindHost:host port:port intoAddress4:&address4 address6:&address6];
	if(gai_error)
	{
		if(errPtr)
		{
			NSString *errMsg = [NSString stringWithCString:gai_strerror(gai_error) encoding:NSASCIIStringEncoding];
			NSDictionary *info = [NSDictionary dictionaryWithObject:errMsg forKey:NSLocalizedDescriptionKey];
			
			*errPtr = [NSError errorWithDomain:@"kCFStreamErrorDomainNetDB" code:gai_error userInfo:info];
		}
		return NO;
	}
	
	NSAssert((address4), @"address4 is nil");
	
	// Set the SO_REUSEADDR flags
	
	int reuseOn = 1;
	if (theSocket4)	setsockopt(CFSocketGetNative(theSocket4), SOL_SOCKET, SO_REUSEADDR, &reuseOn, sizeof(reuseOn));
	
	// Bind the sockets
	
	if(address4)
	{
		if(theSocket4)
		{
			CFSocketError error = CFSocketSetAddress(theSocket4, (__bridge CFDataRef)address4);
			if(error != kCFSocketSuccess)
			{
				if(errPtr) *errPtr = [self getSocketError];
				return NO;
			}
		}
	}
	
	theFlags |= kDidBind;
	return YES;
}

/**
 * By default, the underlying socket in the OS will not allow you to send broadcast messages.
 * In order to send broadcast messages, you need to enable this functionality in the socket.
 * 
 * A broadcast is a UDP message to addresses like "192.168.255.255" or "255.255.255.255" that is
 * delivered to every host on the network.
 * The reason this is generally disabled by default is to prevent
 * accidental broadcast messages from flooding the network.
**/
- (BOOL)enableBroadcast:(BOOL)flag error:(NSError **)errPtr
{
	if (theSocket4)
	{
		int value = flag ? 1 : 0;
		int error = setsockopt(CFSocketGetNative(theSocket4), SOL_SOCKET, SO_BROADCAST,
						   (const void *)&value, sizeof(value));
		if(error)
		{
			if(errPtr)
			{
				NSString *errMsg = @"Unable to enable broadcast message sending";
				NSDictionary *info = [NSDictionary dictionaryWithObject:errMsg forKey:NSLocalizedDescriptionKey];
				
				*errPtr = [NSError errorWithDomain:@"kCFStreamErrorDomainPOSIX" code:error userInfo:info];
			}
			return NO;
		}
	}
	
	// IPv6 does not implement broadcast, the ability to send a packet to all hosts on the attached link.
	// The same effect can be achieved by sending a packet to the link-local all hosts multicast group.
	
	return YES;
}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
#pragma mark Disconnect Implementation:
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

- (void)closeSocket4
{
	if (theSocket4 != NULL)
	{
		CFSocketInvalidate(theSocket4);
		CFRelease(theSocket4);
		theSocket4 = NULL;
	}
	if (theSource4 != NULL)
	{
		CFRelease(theSource4);
		theSource4 = NULL;
	}
}

- (void)close
{
	[self closeSocket4];
	
	theFlags |= kDidClose;
}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
#pragma mark Errors
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

/**
 * Returns a standard error object for the current errno value.
 * Errno is used for low-level BSD socket errors.
**/
- (NSError *)getErrnoError
{
	NSString *errorMsg = [NSString stringWithUTF8String:strerror(errno)];
	NSDictionary *userInfo = [NSDictionary dictionaryWithObject:errorMsg forKey:NSLocalizedDescriptionKey];
	
	return [NSError errorWithDomain:NSPOSIXErrorDomain code:errno userInfo:userInfo];
}

/**
 * Returns a standard error message for a CFSocket error.
 * Unfortunately, CFSocket offers no feedback on its errors.
**/
- (NSError *)getSocketError
{
	NSString *errMsg = @"General CFSocket error";
	NSDictionary *info = [NSDictionary dictionaryWithObject:errMsg forKey:NSLocalizedDescriptionKey];
	
	return [NSError errorWithDomain:SyncUdpSocketErrorDomain code:SyncUdpSocketCFSocketError userInfo:info];
}

- (NSError *)getIPv4UnavailableError
{
	NSString *errMsg = @"IPv4 is unavailable due to binding/connecting using IPv6 only";
	NSDictionary *info = [NSDictionary dictionaryWithObject:errMsg forKey:NSLocalizedDescriptionKey];
	
	return [NSError errorWithDomain:SyncUdpSocketErrorDomain code:SyncUdpSocketIPv4Unavailable userInfo:info];
}

- (NSError *)getIPv6UnavailableError
{
	NSString *errMsg = @"IPv6 is unavailable due to binding/connecting using IPv4 only or is not supported on this platform";
	NSDictionary *info = [NSDictionary dictionaryWithObject:errMsg forKey:NSLocalizedDescriptionKey];
	
	return [NSError errorWithDomain:SyncUdpSocketErrorDomain code:SyncUdpSocketIPv6Unavailable userInfo:info];
}

- (NSError *)getSendTimeoutError
{
	NSString *errMsg = @"Send operation timed out";
	NSDictionary *info = [NSDictionary dictionaryWithObject:errMsg forKey:NSLocalizedDescriptionKey];
	
	return [NSError errorWithDomain:SyncUdpSocketErrorDomain code:SyncUdpSocketSendTimeoutError userInfo:info];
}
- (NSError *)getReceiveTimeoutError
{
	NSString *errMsg = @"Receive operation timed out";
	NSDictionary *info = [NSDictionary dictionaryWithObject:errMsg forKey:NSLocalizedDescriptionKey];
	
	return [NSError errorWithDomain:SyncUdpSocketErrorDomain code:SyncUdpSocketReceiveTimeoutError userInfo:info];
}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
#pragma mark Diagnostics
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

- (NSString *)localHost
{
	if(cachedLocalHost) return cachedLocalHost;
	return [self localHost:theSocket4];
}

- (UInt16)localPort
{
	if(cachedLocalPort > 0) return cachedLocalPort;
	return [self localPort:theSocket4];
}


- (NSString *)localHost:(CFSocketRef)theSocket
{
	if (theSocket == NULL) return nil;
	
	// Unfortunately we can't use CFSocketCopyAddress.
	// The CFSocket library caches the address the first time you call CFSocketCopyAddress.
	// So if this is called prior to binding/connecting/sending, it won't be updated again when necessary,
	// and will continue to return the old value of the socket address.
	
	NSString *result = nil;
	
	if (theSocket == theSocket4)
	{
		struct sockaddr_in sockaddr4;
		socklen_t sockaddr4len = sizeof(sockaddr4);
		
		if (getsockname(CFSocketGetNative(theSocket), (struct sockaddr *)&sockaddr4, &sockaddr4len) < 0)
		{
			return nil;
		}
		result = [self addressHost4:&sockaddr4];
	}
	else
	{
		struct sockaddr_in6 sockaddr6;
		socklen_t sockaddr6len = sizeof(sockaddr6);
		
		if (getsockname(CFSocketGetNative(theSocket), (struct sockaddr *)&sockaddr6, &sockaddr6len) < 0)
		{
			return nil;
		}
		result = [self addressHost6:&sockaddr6];
	}
	
	if (theFlags & kDidBind)
	{
		cachedLocalHost = [result copy];
	}
	
	return result;
}

- (UInt16)localPort:(CFSocketRef)theSocket
{
	if (theSocket == NULL) return 0;
	
	// Unfortunately we can't use CFSocketCopyAddress.
	// The CFSocket library caches the address the first time you call CFSocketCopyAddress.
	// So if this is called prior to binding/connecting/sending, it won't be updated again when necessary,
	// and will continue to return the old value of the socket address.
	
	UInt16 result = 0;
	
	if (theSocket == theSocket4)
	{
		struct sockaddr_in sockaddr4;
		socklen_t sockaddr4len = sizeof(sockaddr4);
		
		if (getsockname(CFSocketGetNative(theSocket), (struct sockaddr *)&sockaddr4, &sockaddr4len) < 0)
		{
			return 0;
		}
		result = ntohs(sockaddr4.sin_port);
	}
	else
	{
		struct sockaddr_in6 sockaddr6;
		socklen_t sockaddr6len = sizeof(sockaddr6);
		
		if (getsockname(CFSocketGetNative(theSocket), (struct sockaddr *)&sockaddr6, &sockaddr6len) < 0)
		{
			return 0;
		}
		result = ntohs(sockaddr6.sin6_port);
	}
	
	if (theFlags & kDidBind)
	{
		cachedLocalPort = result;
	}
	
	return result;
}
- (BOOL)isClosed
{
	return (theFlags & kDidClose) ? YES : NO;
}

- (BOOL)isIPv4
{
	return (theSocket4 != NULL);
}

- (unsigned int)maximumTransmissionUnit
{
	CFSocketNativeHandle theNativeSocket;
	if(theSocket4)
		theNativeSocket = CFSocketGetNative(theSocket4);
	else
		return 0;
	
	if(theNativeSocket == 0)
	{
		return 0;
	}
	
	struct ifreq ifr;
	bzero(&ifr, sizeof(ifr));
	
	if(if_indextoname(theNativeSocket, ifr.ifr_name) == NULL)
	{
		return 0;
	}
	
	if(ioctl(theNativeSocket, SIOCGIFMTU, &ifr) >= 0)
	{
		return ifr.ifr_mtu;
	}
	
	return 0;
}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
#pragma mark Synchronized Methods
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

- (void) doSyncSend:(NSData *) data toHost:(NSString *) host port:(UInt16) port;
{
    ssize_t result;
    CFSocketNativeHandle theNativeSocket = CFSocketGetNative(theSocket4);
    
    NSData *address4 = nil, *address6 = nil;
	[self convertForSendHost:host port:port intoAddress4:&address4 address6:&address6];
    
    const void *buf  = [data bytes];
    NSUInteger bufSize = [data length];
    
    const void *dst  = [address4 bytes];
    NSUInteger dstSize = [address4 length];
    
    result = sendto(theNativeSocket, buf, (size_t)bufSize, 0, dst, (socklen_t)dstSize);
}

- (NSData *) doSyncReceive
{    
    ssize_t result;    
    CFSocketNativeHandle theNativeSocket = CFSocketGetNative(theSocket4);
    
    // Allocate buffer for recvfrom operation.
    // If the operation is successful, we'll realloc the buffer to the appropriate size,
    // and create an NSData wrapper around it without needing to copy any bytes around.
    void *buf = malloc(maxReceiveBufferSize);
    size_t bufSize = maxReceiveBufferSize;
    
    struct sockaddr_in sockaddr4;
    socklen_t sockaddr4len = sizeof(sockaddr4);
    
    result = recvfrom(theNativeSocket, buf, bufSize, 0, (struct sockaddr *)&sockaddr4, &sockaddr4len);
    
    if(result >= 0)
    {
        if(result != bufSize)
        {
            buf = realloc(buf, result);
        }
        
        return [[NSData alloc] initWithBytesNoCopy:buf
                                            length:result
                                      freeWhenDone:YES];
    }
    
    return nil;
}

@end
