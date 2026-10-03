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
package org.rd.fullstack.springbooteda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import org.rd.fullstack.springbooteda.config.Application;
import org.rd.fullstack.springbooteda.util.token.EventToken;
import org.rd.fullstack.springbooteda.util.token.EventTokenPayload;
import org.rd.fullstack.springbooteda.util.token.ShuffleBits;
import org.rd.fullstack.springbooteda.util.token.TokenToolskit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = Application.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Testing the tool for event token management.")
public class T1500_TokenToolskit_UT_Tests {

    private static final Logger logger = 
        LoggerFactory.getLogger(T1500_TokenToolskit_UT_Tests.class);

    @Autowired
    private TokenToolskit tokenToolskit;

    // Fictitious data used across the tests below.
    private static final byte  ENV_ID     = 1;
    private static final short SYSTEM_ID  = 2;
    private static final short SRV_ID     = 3;
    private static final long  REQUEST_ID = 123456789L;
    private static final String CST_DELIMITER_FOR_TEST = "-";

    private static EventToken newEventToken() {
        EventToken token = new EventToken();
        token.setPayload(new EventTokenPayload(ENV_ID, SYSTEM_ID, SRV_ID, REQUEST_ID));
        return token;
    }

    @Test
    @Order(1)
    public void evtTokenPayload_accessorsAndEquality() throws Exception {
        EventTokenPayload payload = new EventTokenPayload(ENV_ID, SYSTEM_ID, SRV_ID, REQUEST_ID);

        assertEquals(ENV_ID, payload.envId(), "The envId should match the value provided to the constructor.");
        assertEquals(SYSTEM_ID, payload.systemId(), "The systemId should match the value provided to the constructor.");
        assertEquals(SRV_ID, payload.srvId(), "The srvId should match the value provided to the constructor.");
        assertEquals(REQUEST_ID, payload.requestId(), "The requestId should match the value provided to the constructor.");

        assertEquals(new EventTokenPayload(ENV_ID, SYSTEM_ID, SRV_ID, REQUEST_ID), payload,
            "Two payloads built from the same values should be equal (record semantics).");
    }

    @Test
    @Order(2)
    public void evtToken_defaultConstructor() throws Exception {
        EventToken token = new EventToken();

        assertEquals((short) 1, token.getType(), "EventToken should default to type 1.");
        assertNull(token.getPayload(), "The payload should be null by default.");
    }

    @Test
    @Order(3)
    public void evtToken_validate_rejectsNullPayload() throws Exception {
        EventToken token = new EventToken();
        assertThrows(IllegalArgumentException.class, token::validate,
            "validate() should reject a token with no payload.");
    }

    @Test
    @Order(4)
    public void evtToken_validate_rejectsNegativeValues() throws Exception {
        EventToken token = new EventToken();
        token.setPayload(new EventTokenPayload(ENV_ID, (short) -1, SRV_ID, REQUEST_ID));
        assertThrows(IllegalArgumentException.class, token::validate,
            "validate() should reject a negative systemId.");

        token.setPayload(new EventTokenPayload(ENV_ID, SYSTEM_ID, (short) -1, REQUEST_ID));
        assertThrows(IllegalArgumentException.class, token::validate,
            "validate() should reject a negative srvId.");

        token.setPayload(new EventTokenPayload(ENV_ID, SYSTEM_ID, SRV_ID, -1L));
        assertThrows(IllegalArgumentException.class, token::validate,
            "validate() should reject a negative requestId.");

        token.setPayload(new EventTokenPayload((byte) -56, SYSTEM_ID, SRV_ID, REQUEST_ID));
        assertThrows(IllegalArgumentException.class, token::validate,
            "validate() should reject a negative envId.");
    }

    @Test
    @Order(5)
    public void evtToken_toBytes_thenFromBytes_roundTrip() throws Exception {
        EventToken original = newEventToken();
        byte[] bytes = original.toBytes();

        assertNotNull(bytes, "toBytes() should not return null for a valid token.");
        assertEquals(15, bytes.length, "An EventToken must serialize to exactly 15 bytes.");

        EventToken restored = new EventToken();
        restored.fromBytes(bytes);

        assertEquals(original.getPayload(), restored.getPayload(),
            "Deserializing the bytes produced by toBytes() should restore an equal payload.");
        logger.info("EventToken toBytes()/fromBytes() round trip preserves the payload.");
    }

    @Test
    @Order(6)
    public void evtToken_toBytes_rejectsInvalidToken() throws Exception {
        EventToken token = new EventToken(); // No payload set.
        assertThrows(UnsupportedOperationException.class, token::toBytes,
            "toBytes() should refuse to serialize an invalid token.");
        logger.info("EventToken.toBytes() rejects an invalid token as expected.");
    }

    @Test
    @Order(7)
    public void evtToken_fromBytes_rejectsNullOrTooShort() throws Exception {
        EventToken token = new EventToken();

        assertThrows(IllegalArgumentException.class, () -> token.fromBytes(null),
            "fromBytes() should reject a null array.");
        assertThrows(IllegalArgumentException.class, () -> token.fromBytes(new byte[] {1, 2, 3}),
            "fromBytes() should reject an array shorter than 15 bytes.");
    }

    @Test
    @Order(8)
    public void evtToken_fromBytes_rejectsWrongType() throws Exception {
        EventToken source = newEventToken();
        byte[] bytes = source.toBytes();
        bytes[1] = (byte) (bytes[1] + 1); // Corrupt the low byte of the 2-byte "type" header.

        EventToken token = new EventToken();
        assertThrows(IllegalArgumentException.class, () -> token.fromBytes(bytes),
            "fromBytes() should reject a byte array whose type header does not match.");
        logger.info("EventToken.fromBytes() rejects a mismatched type header as expected.");
    }

    @Test
    @Order(9)
    public void shuffleBits_forwardThenReverse_isIdentityOnSmallPositiveValues() throws Exception {

        int original = 123456789; // A small positive value that fits in 32 bits.

        ShuffleBits forward = new ShuffleBits();
        forward.init(ShuffleBits.CST_FWD, ShuffleBits.SEED_RATIO, ShuffleBits.SEED_RATIO);
        forward.update(original);
        int mixed = forward.finale();

        ShuffleBits reverse = new ShuffleBits();
        reverse.init(ShuffleBits.CST_RWD, ShuffleBits.SEED_RATIO, ShuffleBits.SEED_RATIO);
        reverse.update(mixed);
        int restored = reverse.finale();

        assertEquals(original, restored, "reverse(forward(x)) should return the original 32-bit value.");
    }

    @Test
    @Order(10)
    public void shuffleBits_update_acceptsFullIntRange() throws Exception {
        // update() takes a raw 32-bit pattern: every int (including negative
        // ones, i.e. values whose top bit is set) is a valid input and must
        // not throw. This guards against reintroducing a sign-extension bug.
        ShuffleBits sb = new ShuffleBits();
        sb.update(-1);
        sb.update(Integer.MIN_VALUE);
        sb.update(Integer.MAX_VALUE);
        sb.update(0);
    }

    @Test
    @Order(11)
    public void tokenToolskit_encode_rejectsNullFormat() throws Exception {
        EventToken token = newEventToken();
        assertThrows(IllegalArgumentException.class, () -> tokenToolskit.encode(token, null),
            "encode() should reject a null format.");
    }

    @Test
    @Order(12)
    public void tokenToolskit_encode_rejectsInvalidToken() throws Exception {
        EventToken token = new EventToken(); // No payload set.
        assertThrows(Exception.class, () -> tokenToolskit.encode(token, TokenToolskit.Format.TOKEN_ONLY),
            "encode() should reject a token that fails validate().");
    }

    @Test
    @Order(13)
    public void tokenToolskit_decode_rejectsNullEmptyOrTooShort() throws Exception {
        assertThrows(Exception.class, () -> tokenToolskit.decode(null, TokenToolskit.Format.TOKEN_AND_MAC),
            "decode() should reject a null token.");
        assertThrows(Exception.class, () -> tokenToolskit.decode("", TokenToolskit.Format.TOKEN_AND_MAC),
            "decode() should reject an empty token.");
        assertThrows(Exception.class, () -> tokenToolskit.decode("ABC", TokenToolskit.Format.TOKEN_AND_MAC),
            "decode() should reject a token that is too short.");
    }

    @Test
    @Order(14)
    @DisplayName("decode() rejects a null format.")
    public void tokenToolskit_decode_rejectsNullFormat() throws Exception {
        EventToken token = newEventToken();
        String encoded = tokenToolskit.encode(token, TokenToolskit.Format.TOKEN_AND_MAC);
        assertThrows(IllegalArgumentException.class, () -> tokenToolskit.decode(encoded, null),
            "decode() should reject a null format.");
    }

    @Test
    @Order(15)
    public void tokenToolskit_encode_isDeterministicPerFormat() throws Exception {
        EventToken token = newEventToken();

        String first  = tokenToolskit.encode(token, TokenToolskit.Format.NO_DASH_NO_MAC);
        String second = tokenToolskit.encode(newEventToken(), TokenToolskit.Format.NO_DASH_NO_MAC);

        assertNotNull(first, "encode() should return a non-null string.");
        assertEquals(first, second, "Encoding the same payload twice should produce the same token.");
        logger.info("TokenToolskit.encode() is deterministic for a given payload/format.");
    }

    @Test
    @Order(16)
    public void tokenToolskit_encodeThenDecode_roundTrip_tokenOnly() throws Exception {
        EventToken token = newEventToken();
        String encoded = tokenToolskit.encode(token, TokenToolskit.Format.TOKEN_ONLY);
        assertNotNull(encoded, "encode() should return a non-null string.");
        logger.info("Encoded TOKEN_ONLY token: {}", encoded);

        EventToken decoded = (EventToken) tokenToolskit.decode(encoded, TokenToolskit.Format.TOKEN_ONLY);
        assertEquals(token.getPayload(), decoded.getPayload(),
            "Decoding an encoded token should restore the original payload.");
        logger.info("TokenToolskit round trip (TOKEN_ONLY) preserves the payload.");
    }

    @Test
    @Order(17)
    public void tokenToolskit_encodeThenDecode_roundTrip_noDashNoMac() throws Exception {
        EventToken token = newEventToken();
        String encoded = tokenToolskit.encode(token, TokenToolskit.Format.NO_DASH_NO_MAC);
        assertNotNull(encoded, "encode() should return a non-null string.");
        assertFalse(encoded.contains("-"), "NO_DASH_NO_MAC tokens must not contain dashes.");
        logger.info("Encoded NO_DASH_NO_MAC token: {}", encoded);

        EventToken decoded = (EventToken) tokenToolskit.decode(encoded, TokenToolskit.Format.NO_DASH_NO_MAC);
        assertEquals(token.getPayload(), decoded.getPayload(),
            "Decoding an encoded token should restore the original payload.");
        logger.info("TokenToolskit round trip (NO_DASH_NO_MAC) preserves the payload.");
    }

    @Test
    @Order(18)
    public void tokenToolskit_encodeThenDecode_roundTrip_tokenAndMac() throws Exception {
        EventToken token = newEventToken();
        String encoded = tokenToolskit.encode(token, TokenToolskit.Format.TOKEN_AND_MAC);
        assertNotNull(encoded, "encode() should return a non-null string.");
        logger.info("Encoded TOKEN_AND_MAC token: {}", encoded);

        EventToken decoded = (EventToken) tokenToolskit.decode(encoded, TokenToolskit.Format.TOKEN_AND_MAC);
        assertEquals(token.getPayload(), decoded.getPayload(),
            "Decoding an encoded token should restore the original payload.");
        logger.info("TokenToolskit round trip (TOKEN_AND_MAC) preserves the payload.");
    }

    @Test
    @Order(19)
    public void tokenToolskit_decode_detectsTamperedSignature() throws Exception {
        EventToken token = newEventToken();
        String encoded = tokenToolskit.encode(token, TokenToolskit.Format.TOKEN_AND_MAC);

        // Flip the last character (part of the appended MAC) to simulate tampering.
        char last = encoded.charAt(encoded.length() - 1);
        char replacement = (last == '0') ? '1' : '0';
        String tampered = encoded.substring(0, encoded.length() - 1) + replacement;

        assertNotEquals(encoded, tampered, "Test setup sanity check: the tampered token must differ from the original.");
        assertThrows(Exception.class, () -> tokenToolskit.decode(tampered, TokenToolskit.Format.TOKEN_AND_MAC),
            "decode() should reject a token whose signature was tampered with.");
        logger.info("TokenToolskit.decode() detects a tampered signature as expected.");
    }

    @Test
    @Order(20)
    @DisplayName("decode() cannot be tricked into skipping MAC verification by stripping the MAC.")
    public void tokenToolskit_decode_cannotBypassMacByStrippingIt() throws Exception {
        EventToken token = newEventToken();
        String encoded = tokenToolskit.encode(token, TokenToolskit.Format.TOKEN_AND_MAC);

        // Strip the appended "-<4 hex chars>" MAC suffix, leaving only the base32 payload.
        // Its length is a multiple of 8 (Base32 of a multiple-of-5 byte array never needs
        // padding), i.e. it now looks exactly like a TOKEN_ONLY/NO_DASH_NO_MAC token.
        int dashIndex = encoded.lastIndexOf(CST_DELIMITER_FOR_TEST);
        String strippedOfMac = encoded.substring(0, dashIndex).replaceAll(CST_DELIMITER_FOR_TEST, "");
        assertEquals(0, strippedOfMac.length() % 8,
            "Test setup sanity check: the stripped payload should look MAC-less by length.");

        // A caller that still expects TOKEN_AND_MAC (the only format actually used in
        // production) must NOT accept this stripped-down, unverified string.
        assertThrows(Exception.class, () -> tokenToolskit.decode(strippedOfMac, TokenToolskit.Format.TOKEN_AND_MAC),
            "decode() must not silently skip MAC verification just because the payload length allows it.");
        logger.info("TokenToolskit.decode() cannot be downgraded to an unverified token by stripping the MAC.");
    }

    @Test
    @Order(21)
    public void tokenToolskit_customSecretKey_producesIncompatibleSignatures() throws Exception {
        TokenToolskit other = TokenToolskit.builder().secretKey("a-different-secret-key").build();
        EventToken token = newEventToken();

        String encoded = other.encode(token, TokenToolskit.Format.TOKEN_AND_MAC);

        assertThrows(Exception.class, () -> tokenToolskit.decode(encoded, TokenToolskit.Format.TOKEN_AND_MAC),
            "A token signed with a different secret key should fail signature verification.");
        logger.info("Two TokenToolskit instances with different secret keys are not signature-compatible.");
    }
}
