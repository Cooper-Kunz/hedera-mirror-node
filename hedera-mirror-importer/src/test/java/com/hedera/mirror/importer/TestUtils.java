package com.hedera.mirror.importer;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import com.hedera.mirror.importer.util.Utility;

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.time.Instant;

public final class TestUtils {
    public static AccountID toAccountId(String accountId) {
        var parts = accountId.split("\\.");
        return AccountID.newBuilder().setShardNum(Long.parseLong(parts[0])).setRealmNum(Long.parseLong(parts[1]))
                .setAccountNum(Long.parseLong(parts[2])).build();
    }

    public static TransactionID toTransactionId(String transactionId) {
        var parts = transactionId.split("-");
        return TransactionID.newBuilder().setAccountID(toAccountId(parts[0]))
                .setTransactionValidStart(toTimestamp(Long.valueOf(parts[1]))).build();
    }

    public static Timestamp toTimestamp(long nanosecondsSinceEpoch) {
        return Utility.instantToTimestamp(Instant.ofEpochSecond(0, nanosecondsSinceEpoch));
    }

    public static Timestamp toTimestamp(long seconds, long nanoseconds) {
        return Timestamp.newBuilder().setSeconds(seconds).setNanos((int) nanoseconds).build();
    }

    public static byte[] toByteArray(Key key) {
        return (null == key) ? null : key.toByteArray();
    }
}
