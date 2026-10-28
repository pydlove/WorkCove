#import <Foundation/Foundation.h>
#import <objc/message.h>

typedef long (*LongGetter)(id, SEL);
typedef long (*DayGetter)(id, SEL, long);

static NSDictionary *unavailable(NSString *message) {
    return @{
        @"serviceAvailable": @NO,
        @"totalInput": @0,
        @"currentSpeed": @0,
        @"historySpeed": @0,
        @"days": @[],
        @"message": message ?: @"搜狗输入法统计服务不可用"
    };
}

int main(void) {
    @autoreleasepool {
        NSDictionary *response = nil;
        @try {
            id proxy = [NSConnection
                rootProxyForConnectionWithRegisteredName:@"SOGOU_NOTIFY_SERVER"
                host:nil];
            if (!proxy) {
                response = unavailable(@"搜狗输入法统计服务未启动");
            } else {
                SEL totalSelector =
                    sel_registerName("getInputTotalCountFromSogouInput");
                SEL daySelector =
                    sel_registerName("getInputCountByDayFromSogouInput:");
                if (![proxy respondsToSelector:totalSelector]
                    || ![proxy respondsToSelector:daySelector]) {
                    response = unavailable(@"搜狗输入法版本不支持本地统计接口");
                } else {
                    long total = ((LongGetter)objc_msgSend)(
                        proxy,
                        totalSelector);
                    SEL currentSpeedSelector =
                        sel_registerName("getInputCurrentSpeedFromSogouInput");
                    SEL historySpeedSelector =
                        sel_registerName("getInputHistorySpeedFromSogouInput");
                    SEL historySpeedTimeSelector =
                        sel_registerName("getInputHistorySpeedTimeFromSogouInput");
                    long currentSpeed = [proxy respondsToSelector:currentSpeedSelector]
                        ? ((LongGetter)objc_msgSend)(proxy, currentSpeedSelector)
                        : 0;
                    long historySpeed = [proxy respondsToSelector:historySpeedSelector]
                        ? ((LongGetter)objc_msgSend)(proxy, historySpeedSelector)
                        : 0;
                    long historySpeedAt = [proxy respondsToSelector:historySpeedTimeSelector]
                        ? ((LongGetter)objc_msgSend)(proxy, historySpeedTimeSelector)
                        : 0;

                    NSCalendar *calendar = [NSCalendar currentCalendar];
                    NSDate *now = [NSDate date];
                    NSDate *startOfToday = [calendar startOfDayForDate:now];
                    NSDateFormatter *formatter = [[NSDateFormatter alloc] init];
                    formatter.locale =
                        [[NSLocale alloc] initWithLocaleIdentifier:@"en_US_POSIX"];
                    formatter.dateFormat = @"yyyy-MM-dd";
                    NSMutableArray *days = [NSMutableArray array];
                    for (int offset = 29; offset >= 0; offset -= 1) {
                        NSDate *day = [calendar
                            dateByAddingUnit:NSCalendarUnitDay
                            value:-offset
                            toDate:startOfToday
                            options:0];
                        NSDate *noon = [calendar
                            dateByAddingUnit:NSCalendarUnitHour
                            value:12
                            toDate:day
                            options:0];
                        long count = ((DayGetter)objc_msgSend)(
                            proxy,
                            daySelector,
                            (long)[noon timeIntervalSince1970]);
                        [days addObject:@{
                            @"date": [formatter stringFromDate:day],
                            @"count": @(count)
                        }];
                    }
                    response = @{
                        @"serviceAvailable": @YES,
                        @"totalInput": @(total),
                        @"currentSpeed": @(currentSpeed),
                        @"historySpeed": @(historySpeed),
                        @"historySpeedAt": @(historySpeedAt),
                        @"days": days,
                        @"message": @""
                    };
                }
            }
        } @catch (NSException *exception) {
            response = unavailable(exception.reason ?: @"读取搜狗统计失败");
        }

        NSError *error = nil;
        NSData *data = [NSJSONSerialization
            dataWithJSONObject:response
            options:0
            error:&error];
        if (!data || error) {
            printf("{\"serviceAvailable\":false,\"totalInput\":0,"
                   "\"currentSpeed\":0,\"historySpeed\":0,\"days\":[],"
                   "\"message\":\"无法序列化搜狗统计结果\"}\n");
            return 2;
        }
        NSString *json = [[NSString alloc]
            initWithData:data
            encoding:NSUTF8StringEncoding];
        printf("%s\n", json.UTF8String);
    }
    return 0;
}
