//
// Copyright (c) ZeroC, Inc. All rights reserved.
//

#import <ImplicitContextI.h>
#import <Util.h>

@implementation ICEImplicitContext

-(id) init:(Ice::ImplicitContext*)implicitContext
{
    self = [super init];
    if(self)
    {
        self->implicitContext_ = implicitContext;
        self->implicitContext_->__incRef();
    }
    return self;
}

+(id) implicitContextWithImplicitContext:(Ice::ImplicitContext*)implicitContext
{
    if(!implicitContext)
    {
        return nil;
    }
    else
    {
        return [[[ICEImplicitContext alloc] init:implicitContext] autorelease];
    }
}

-(void) dealloc
{
    self->implicitContext_->__decRef();
    [super dealloc];
}

-(ICEContext*) getContext
{
    return [toNSDictionary(implicitContext_->getContext()) autorelease];
}

-(void) setContext:(ICEContext*)context
{
    Ice::Context ctx;
    fromNSDictionary(context, ctx);
    implicitContext_->setContext(ctx);
}

-(BOOL) containsKey:(NSString*)key
{
    return implicitContext_->containsKey(fromNSString(key));
}

-(NSMutableString*) get:(NSString*)key
{
    if(implicitContext_->containsKey(fromNSString(key)))
    {
        return [toNSMutableString(implicitContext_->get(fromNSString(key))) autorelease];
    }
    else
    {
        return nil;
    }
}

-(NSMutableString*) put:(NSString*)key value:(NSString*)value
{
    return [toNSMutableString(implicitContext_->put(fromNSString(key), fromNSString(value))) autorelease];
}

-(NSMutableString*) remove:(NSString*)key
{
    return [toNSMutableString(implicitContext_->remove(fromNSString(key))) autorelease];
}

@end
