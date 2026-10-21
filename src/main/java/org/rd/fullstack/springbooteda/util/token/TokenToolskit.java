/*
 * Copyright 2026; Réal Demers.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.rd.fullstack.springbooteda.util.token;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Base32;
import org.apache.commons.codec.binary.Hex;

public class TokenToolskit {

    private static final String CST_DELIMITER      = "-";
    private static final String CST_EMPTY          = "";
    private static final int CST_SHORT_FULL_LENGTH = Short.SIZE / 8;
    private static final int CST_INT_FULL_LENGTH   = Integer.SIZE / 8;
    private static final int CST_INT_HALF_LENGTH   = CST_INT_FULL_LENGTH / 2;

    private static final int CST_BASE32_ENCRYPT = 5;
    private static final int CST_BASE32_DECRYPT = 8;

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String REGEX_SPLIT = "(?<=\\G.{4})";
    private static final byte[] CST_DEFAULT_SECRET_KEY = new byte[] {
            (byte) 0x123, (byte) 0x045, (byte) 0x067, (byte) 0x089, 
            (byte) 0x010, (byte) 0x112, (byte) 0x120, (byte) 0x013, 
            (byte) 0x014, (byte) 0x150, (byte) 0x016, (byte) 0x017, 
            (byte) 0x180, (byte) 0x019, (byte) 0x200, (byte) 0x121, 
            (byte) 0x222, (byte) 0x233, (byte) 0x024, (byte) 0x250, 
            (byte) 0x261, (byte) 0x027, (byte) 0x028, (byte) 0x029
    };

    private static final Base32 BASE32 = new Base32();
    private final  byte[] secretKey;

    public enum Format {
        TOKEN_ONLY, TOKEN_AND_MAC, NO_DASH_NO_MAC
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String secretKey = null;

        public Builder secretKey(String secretKey) {
            this.secretKey = secretKey;
            return this;
        }

        public TokenToolskit build() throws Exception {
		    return new TokenToolskit(this.secretKey);
        }
    }

    public TokenToolskit() {
        this.secretKey = CST_DEFAULT_SECRET_KEY;
    }

    public TokenToolskit(String secretKey) {
        if (secretKey == null)
            this.secretKey = CST_DEFAULT_SECRET_KEY;
        else
            this.secretKey = secretKey.getBytes(StandardCharsets.UTF_8);
    }

    public String encode(AbstractToken<?> token, Format format) throws Exception {

        // Validation.
        if (format == null)
            throw new IllegalArgumentException("Format cannot be null.");
        token.validate();

        byte[] signBytes  = null;
        byte[] tokenBytes = token.toBytes();
        if (tokenBytes == null)
            throw new IllegalArgumentException("Token serialization returned null byte array.");
        if (tokenBytes.length % CST_BASE32_ENCRYPT != 0)
            throw new IllegalArgumentException("Token length is not a multiple of 5.");

        String strTokenBase32 = null;
        String strTokenFinal  = null;

        try {
            tokenBytes = forwardShuffle(tokenBytes);
            signBytes  = ByteBuffer.allocate(CST_SHORT_FULL_LENGTH)
                .putShort(getSignature(tokenBytes)).array();

            strTokenBase32 = new String(BASE32.encode(tokenBytes));
            if (format != Format.NO_DASH_NO_MAC) {
                String[] parts = strTokenBase32.split(REGEX_SPLIT);
                strTokenFinal = String.join(CST_DELIMITER, parts);
            }  else {
                strTokenFinal = strTokenBase32;
            }

            if (format == Format.TOKEN_AND_MAC)
                strTokenFinal = strTokenFinal + 
                    CST_DELIMITER + new String(Hex.encodeHex(signBytes, false));

        } catch (Exception ex) {
            throw new Exception("Error encoding token: " + ex.getMessage(), ex);
        }
        return strTokenFinal;
    }

    public AbstractToken<?> decode(String token, Format format) throws Exception {

        // Validation.
        if (format == null)
            throw new IllegalArgumentException("Format cannot be null.");
        if (token == null || token.isEmpty()
                          || token.length() < (CST_BASE32_DECRYPT*3))
            throw new Exception("Token cannot be null or empty or too short.");

        String wrkTokenString = token.replaceAll(CST_DELIMITER, CST_EMPTY);

        byte[] btToken;
        if (format == Format.TOKEN_AND_MAC) {
            // The MAC is mandatory for this format: its presence and length are dictated by
            // the format itself, never guessed from the resulting string length. This is what
            // makes verification unconditional instead of an attacker-influenceable heuristic.
            int macHexLength = CST_SHORT_FULL_LENGTH * 2;
            if (wrkTokenString.length() <= macHexLength)
                throw new Exception("Token is too short to contain a MAC.");

            String wrkSign = wrkTokenString.substring(wrkTokenString.length() - macHexLength);
            wrkTokenString = wrkTokenString.substring(0, wrkTokenString.length() - macHexLength);
            if (wrkTokenString.length() % CST_BASE32_DECRYPT != 0)
                throw new Exception("Token payload length is invalid for a TOKEN_AND_MAC token.");

            btToken = BASE32.decode(wrkTokenString);
            short btSign = ByteBuffer.wrap(Hex.decodeHex(wrkSign)).getShort();
            if (btSign != getSignature(btToken))
                throw new Exception("Token signature does not match.");
        } else {
            if (wrkTokenString.length() % CST_BASE32_DECRYPT != 0)
                throw new Exception("Token length is not a multiple of 8.");
            btToken = BASE32.decode(wrkTokenString);
        }

        EventToken eventToken = new EventToken();
        btToken = reverseShuffle(btToken);
        eventToken.fromBytes(btToken);
        return eventToken;
    }

    private short getSignature(byte[] token) throws Exception {

        if (token == null)
            throw new IllegalArgumentException("Token cannot be null.");

        SecretKeySpec secretKey = new SecretKeySpec(this.secretKey, HMAC_ALGORITHM);
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(secretKey);

        ByteBuffer byteBuffer = ByteBuffer.wrap(mac.doFinal(token));
        short signShort = byteBuffer.getShort();
        while (byteBuffer.hasRemaining())
            signShort = (short) (signShort ^ byteBuffer.getShort());

        return signShort;
    }

    private byte[] forwardShuffle(byte[] tokenBytes) {

        ShuffleBits sb = new ShuffleBits();
        sb.init(ShuffleBits.CST_FWD, ShuffleBits.SEED_RATIO, ShuffleBits.SEED_RATIO);
        
        ByteBuffer swap = ByteBuffer.allocate(CST_INT_FULL_LENGTH);
        int end = tokenBytes.length % CST_INT_FULL_LENGTH;
        
        int value;
        for (int index = tokenBytes.length - CST_INT_FULL_LENGTH; 
                 index >= end; index -= CST_INT_HALF_LENGTH) {
            
            swap.rewind(); swap.put(tokenBytes, index, CST_INT_FULL_LENGTH); swap.rewind();
            value = swap.getInt(); sb.update(value); value = (int) sb.finale();

            swap.rewind(); swap.putInt(value); swap.rewind();
            swap.get(tokenBytes, index, CST_INT_FULL_LENGTH);
        }

        if (tokenBytes.length % CST_INT_FULL_LENGTH > 0) {
            swap.rewind(); swap.put(tokenBytes, 0, CST_INT_FULL_LENGTH); swap.rewind();
            value = swap.getInt(); sb.update(value); value = (int) sb.finale();

            swap.rewind(); swap.putInt(value); swap.rewind();
            swap.get(tokenBytes, 0, CST_INT_FULL_LENGTH);
        }
        return tokenBytes;
    }

    private byte[] reverseShuffle(byte[] tokenBytes) {

        ShuffleBits sb = new ShuffleBits();
        sb.init(ShuffleBits.CST_RWD, ShuffleBits.SEED_RATIO, ShuffleBits.SEED_RATIO);
        ByteBuffer swap = ByteBuffer.allocate(CST_INT_FULL_LENGTH);

        int value;        
        if (tokenBytes.length % CST_INT_FULL_LENGTH > 0) {
            swap.rewind(); swap.put(tokenBytes, 0, CST_INT_FULL_LENGTH); swap.rewind();
            value = swap.getInt(); sb.update(value); value = (int) sb.finale();

            swap.rewind(); swap.putInt(value); swap.rewind();
            swap.get(tokenBytes, 0, CST_INT_FULL_LENGTH);
        }

        int start = tokenBytes.length % CST_INT_FULL_LENGTH;
        for (int index = start; 
                 index <= (tokenBytes.length - CST_INT_FULL_LENGTH); 
                 index += CST_INT_HALF_LENGTH) {
            
            swap.rewind(); swap.put(tokenBytes, index, CST_INT_FULL_LENGTH); swap.rewind();
            value = swap.getInt(); sb.update(value); value = (int) sb.finale();

            swap.rewind(); swap.putInt(value); swap.rewind();
            swap.get(tokenBytes, index, CST_INT_FULL_LENGTH);
        }
        return tokenBytes;
    }
}
