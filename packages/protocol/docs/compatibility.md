# Protocol compatibility

ABOT uses semantic `major.minor` protocol versions. Peers must reject unknown major versions. A minor version may add optional fields or new message types; existing required fields cannot change meaning within a major version.

The initial implementation shipped under the AirBridge name. The v1 wire domains, `_airbridge._tcp.local.` discovery service type, canonical `airbridge.local` schema IDs, and legacy `airbridge://` pairing scheme remain accepted compatibility identifiers. New UI and generated pairing links use ABOT branding and `abot://`.

Every control message has a UUID message ID for replay detection, a transfer ID for correlation, and an RFC 3339 timestamp. Authentication is supplied by the secure channel and session context rather than embedded secret fields in JSON.
