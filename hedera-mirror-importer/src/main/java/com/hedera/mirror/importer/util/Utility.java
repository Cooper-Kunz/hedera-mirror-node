package com.hedera.mirror.importer.util;

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

import static com.hederahashgraph.api.proto.java.Key.KeyCase.ED25519;

import com.google.protobuf.GeneratedMessageV3;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.TextFormat;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import javax.annotation.Nullable;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import com.hedera.mirror.importer.parser.ParserProperties;

@Log4j2
public class Utility {

    private static final Long SCALAR = 1_000_000_000L;
    private static final String EMPTY_HASH = Hex.encodeHexString(new byte[48]);

    /**
     * 1. Extract the Hash of the content of corresponding RecordStream file. This Hash is the signed Content of this
     * signature 2. Extract signature from the file.
     *
     * @param file
     * @return
     */
    public static Pair<byte[], byte[]> extractHashAndSigFromFile(File file) {
        byte[] sig = null;

        if (file.exists() == false) {
            log.info("File does not exist {}", file.getPath());
            return null;
        }

        try (DataInputStream dis = new DataInputStream(new FileInputStream(file))) {
            byte[] fileHash = new byte[48];

            while (dis.available() != 0) {
                byte typeDelimiter = dis.readByte();

                switch (typeDelimiter) {
                    case FileDelimiter.SIGNATURE_TYPE_FILE_HASH:
                        dis.read(fileHash);
                        break;

                    case FileDelimiter.SIGNATURE_TYPE_SIGNATURE:
                        int sigLength = dis.readInt();
                        byte[] sigBytes = new byte[sigLength];
                        dis.readFully(sigBytes);
                        sig = sigBytes;
                        break;
                    default:
                        log.error("Unknown file delimiter {} in signature file {}", typeDelimiter, file);
                        return null;
                }
            }

            return Pair.of(fileHash, sig);
        } catch (Exception e) {
            log.error("Unable to extract hash and signature from file {}", file, e);
        }

        return null;
    }

    /**
     * Calculate SHA384 hash of a balance file
     *
     * @param fileName file name
     * @return byte array of hash value of null if calculating has failed
     */
    public static byte[] getBalanceFileHash(String fileName) {
        try {
            MessageDigest md = MessageDigest.getInstance(FileDelimiter.HASH_ALGORITHM);
            byte[] array = Files.readAllBytes(Paths.get(fileName));
            return md.digest(array);
        } catch (NoSuchAlgorithmException | IOException e) {
            log.error(e);
            return null;
        }
    }

    /**
     * Calculate SHA384 hash of a record file
     *
     * @param filename file name
     * @return byte array of hash value of null if calculating has failed
     */
    public static byte[] getRecordFileHash(String filename) {
        byte[] readFileHash = new byte[48];

        try (DataInputStream dis = new DataInputStream(new FileInputStream(filename))) {
            MessageDigest md = MessageDigest.getInstance(FileDelimiter.HASH_ALGORITHM);
            MessageDigest mdForContent = MessageDigest.getInstance(FileDelimiter.HASH_ALGORITHM);

            int record_format_version = dis.readInt();
            int version = dis.readInt();

            md.update(Utility.integerToBytes(record_format_version));
            md.update(Utility.integerToBytes(version));

            log.debug("Calculating hash for version {} record file: {}", record_format_version, filename);

            while (dis.available() != 0) {

                byte typeDelimiter = dis.readByte();

                switch (typeDelimiter) {
                    case FileDelimiter.RECORD_TYPE_PREV_HASH:
                        md.update(typeDelimiter);
                        dis.read(readFileHash);
                        md.update(readFileHash);
                        break;
                    case FileDelimiter.RECORD_TYPE_RECORD:

                        int byteLength = dis.readInt();
                        byte[] rawBytes = new byte[byteLength];
                        dis.readFully(rawBytes);
                        if (record_format_version >= FileDelimiter.RECORD_FORMAT_VERSION) {
                            mdForContent.update(typeDelimiter);
                            mdForContent.update(Utility.integerToBytes(byteLength));
                            mdForContent.update(rawBytes);
                        } else {
                            md.update(typeDelimiter);
                            md.update(Utility.integerToBytes(byteLength));
                            md.update(rawBytes);
                        }

                        byteLength = dis.readInt();
                        rawBytes = new byte[byteLength];
                        dis.readFully(rawBytes);

                        if (record_format_version >= FileDelimiter.RECORD_FORMAT_VERSION) {
                            mdForContent.update(Utility.integerToBytes(byteLength));
                            mdForContent.update(rawBytes);
                        } else {
                            md.update(Utility.integerToBytes(byteLength));
                            md.update(rawBytes);
                        }
                        break;
                    case FileDelimiter.RECORD_TYPE_SIGNATURE:
                        int sigLength = dis.readInt();
                        byte[] sigBytes = new byte[sigLength];
                        dis.readFully(sigBytes);
                        log.trace("File {} has signature {}", () -> filename, () -> Hex.encodeHexString(sigBytes));
                        break;
                    default:
                        log.error("Unknown record file delimiter {} for file {}", typeDelimiter, filename);
                        return null;
                }
            }

            if (record_format_version == FileDelimiter.RECORD_FORMAT_VERSION) {
                md.update(mdForContent.digest());
            }

            byte[] fileHash = md.digest();
            log.trace("Calculated file hash for the current file {}", () -> Hex.encodeHexString(fileHash));
            return fileHash;
        } catch (Exception e) {
            log.error("Error reading hash for file {}", filename, e);
            return null;
        }
    }

    public static AccountID stringToAccountID(String string) throws IllegalArgumentException {
        if (string == null || string.isEmpty()) {
            throw new IllegalArgumentException("Cannot parse empty string to AccountID");
        }
        String[] strs = string.split("[.]");

        if (strs.length != 3) {
            throw new IllegalArgumentException("Cannot parse string to AccountID: Invalid format.");
        }
        AccountID.Builder idBuilder = AccountID.newBuilder();
        idBuilder.setShardNum(Integer.valueOf(strs[0]))
                .setRealmNum(Integer.valueOf(strs[1]))
                .setAccountNum(Integer.valueOf(strs[2]));
        return idBuilder.build();
    }

    public static byte[] integerToBytes(int number) {
        ByteBuffer b = ByteBuffer.allocate(4);
        b.putInt(number);
        return b.array();
    }

    public static byte[] longToBytes(long number) {
        ByteBuffer b = ByteBuffer.allocate(8);
        b.putLong(number);
        return b.array();
    }

    public static byte booleanToByte(boolean value) {
        return value ? (byte) 1 : (byte) 0;
    }

    public static byte[] instantToBytes(Instant instant) {
        ByteBuffer b = ByteBuffer.allocate(16);
        b.putLong(instant.getEpochSecond()).putLong(instant.getNano());
        return b.array();
    }

    /**
     * @return Timestamp from an instant
     */
    public static Timestamp instantToTimestamp(Instant instant) {
        return Timestamp.newBuilder().setSeconds(instant.getEpochSecond()).setNanos(instant.getNano()).build();
    }

    /**
     * @return string which represents an AccountID
     */
    public static String accountIDToString(AccountID accountID) {
        return String.format("%d.%d.%d", accountID.getShardNum(),
                accountID.getRealmNum(), accountID.getAccountNum());
    }

    public static Instant convertToInstant(Timestamp timestamp) {
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }

    /**
     * print a protobuf Message's content to a String
     *
     * @param message
     * @return
     */
    public static String printProtoMessage(GeneratedMessageV3 message) {
        return TextFormat.shortDebugString(message);
    }

    /**
     * Convert bytes to hex.
     *
     * @param bytes to be converted
     * @return converted HexString
     */
    public static String bytesToHex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        return Hex.encodeHexString(bytes);
    }

    public static String getFileName(String path) {
        int lastIndexOf = path.lastIndexOf("/");
        if (lastIndexOf == -1) {
            return ""; // empty extension
        }
        return path.substring(lastIndexOf + 1);
    }

    /**
     * Convert an Instant to a Long type timestampInNanos
     */
    public static Long convertInstantToNanos(Instant instant) {
        return convertToNanos(instant.getEpochSecond(), instant.getNano());
    }

    /**
     * Converts time in (second, nanos) to time in only nanos.
     */
    public static Long convertToNanos(long second, long nanos) {
        try {
            return Math.addExact(Math.multiplyExact(second, SCALAR), nanos);
        } catch (ArithmeticException e) {
            log.error("Long overflow when converting time to nanos timestamp : {}s {}ns", second, nanos);
            throw e;
        }
    }

    /**
     * Converts time in (second, nanos) to time in only nanos, with a fallback if overflow: If positive overflow, return
     * the max time in the future (Long.MAX_VALUE). If negative overflow, return the max time in the past
     * (Long.MIN_VALUE).
     */
    public static Long convertToNanosMax(long second, long nanos) {
        try {
            return convertToNanos(second, nanos);
        } catch (ArithmeticException ex) {
            return second >= 0 ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
    }

    /**
     * Convert Timestamp to a Long type timeStampInNanos
     */
    public static Long timeStampInNanos(Timestamp timestamp) {
        try {
            if (timestamp == null) {
                return null;
            }
            return Math.addExact(Math.multiplyExact(timestamp.getSeconds(), SCALAR), timestamp.getNanos());
        } catch (ArithmeticException e) {
            throw new ArithmeticException("Long overflow when converting Timestamp to nanos timestamp: " + timestamp);
        }
    }

    public static Long timestampInNanosMax(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }
        return convertToNanosMax(timestamp.getSeconds(), timestamp.getNanos());
    }

    public static boolean hashIsEmpty(String hash) {
        return StringUtils.isBlank(hash) || hash.equals(EMPTY_HASH);
    }

    public static void moveFileToParsedDir(String filePath, ParserProperties parserProperties) {
        Path source = Path.of(filePath);
        String fileName = source.getFileName().toString();
        String dateSubDir = fileName.substring(0, 10).replace("-", File.separator);
        Path destination = parserProperties.getParsedPath().resolve(dateSubDir).resolve(fileName);
        destination.getParent().toFile().mkdirs();

        try {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
            log.trace("Moved {} to {}", source, destination);
        } catch (Exception e) {
            log.error("Error moving file {} to {}", source, destination, e);
        }
    }

    public static void deleteFile(String fileName) {
        try {
            Files.delete(new File(fileName).toPath());
            log.trace("Deleted file {}", fileName);
        } catch (Exception e) {
            log.error("Error deleting file {}", fileName);
        }
    }

    public static void moveOrDeleteParsedFile(String fileName, ParserProperties parserProperties) {
        if (parserProperties.isKeepFiles()) {
            Utility.moveFileToParsedDir(fileName, parserProperties);
        } else {
            Utility.deleteFile(fileName);
        }
    }

    public static void purgeDirectory(Path path) {
        File dir = path.toFile();
        if (!dir.exists()) {
            return;
        }

        for (File file : dir.listFiles()) {
            if (file.isDirectory()) {
                purgeDirectory(file.toPath());
            }
            file.delete();
        }
    }

    public static void ensureDirectory(Path path) {
        if (path == null) {
            throw new IllegalArgumentException("Empty path");
        }

        File directory = path.toFile();
        directory.mkdirs();

        if (!directory.exists()) {
            throw new IllegalStateException("Unable to create directory " + directory.getAbsolutePath());
        }
        if (!directory.isDirectory()) {
            throw new IllegalStateException("Not a directory " + directory.getAbsolutePath());
        }
        if (!directory.canRead() || !directory.canWrite()) {
            throw new IllegalStateException("Insufficient permissions for directory " + directory.getAbsolutePath());
        }
    }

    public static File getResource(String path) {
        ClassLoader[] classLoaders = {Thread
                .currentThread().getContextClassLoader(), Utility.class.getClassLoader(),
                ClassLoader.getSystemClassLoader()};
        URL url = null;

        for (ClassLoader classLoader : classLoaders) {
            if (classLoader != null) {
                url = classLoader.getResource(path);
                if (url != null) {
                    break;
                }
            }
        }

        if (url == null) {
            throw new RuntimeException("Cannot find resource: " + path);
        }

        try {
            return new File(url.toURI().getSchemeSpecificPart());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * If the protobuf encoding of a Key is a single ED25519 key, return the key as a String with lowercase hex
     * encoding.
     *
     * @param protobufKey
     * @return ED25519 public key as a String in hex encoding, or null
     * @throws InvalidProtocolBufferException if the protobufKey is not a valid protobuf encoding of a Key
     *                                        (BasicTypes.proto)
     */
    public static @Nullable
    String protobufKeyToHexIfEd25519OrNull(@Nullable byte[] protobufKey)
            throws InvalidProtocolBufferException {
        if ((null == protobufKey) || (0 == protobufKey.length)) {
            return null;
        }

        var parsedKey = Key.parseFrom(protobufKey);
        if (ED25519 != parsedKey.getKeyCase()) {
            return null;
        }

        return Hex.encodeHexString(parsedKey.getEd25519().toByteArray(), true);
    }

    /**
     * Generates a TransactionID object
     *
     * @param payerAccountId the AccountID of the transaction payer account
     * @return
     */
    public static TransactionID getTransactionId(AccountID payerAccountId) {
        Timestamp validStart = Utility.instantToTimestamp(Instant.now());
        return TransactionID.newBuilder().setAccountID(payerAccountId).setTransactionValidStart(validStart).build();
    }
}
