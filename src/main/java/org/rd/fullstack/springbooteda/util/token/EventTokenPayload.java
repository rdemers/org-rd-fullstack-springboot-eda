package org.rd.fullstack.springbooteda.util.token;

/**
 * EventTokenPayload
 */
public record EventTokenPayload(
    byte  envId,     // Len of short in bytes  = 1.
    short systemId,  // Len of short in bytes  = 2.
    short srvId,     // Len of short in bytes  = 2.
    long  requestId  // Len of long in bytes   = 8.
                     //                        ----
                     // Total length in bytes = 13 + 2 bytes (type) = 15 bytes

    // Important note
    // --------------
    // Normally, a field should be added for the version or the record.
    // However, for the Sandbox, the consumer manipulates the source (REQUEST).
    // This approach makes it easier to implement the "replay" mechanism.
    //
    // In a production implementation, it would be preferable to add a version
    // field (the REQUEST version) to manage record evolution - Idempotency.
) {
}
