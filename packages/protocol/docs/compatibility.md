# Protocol compatibility

AirBridge uses semantic `major.minor` protocol versions. Peers must reject unknown major versions. A minor version may add optional fields or new message types; existing required fields cannot change meaning within a major version.

Every control message has a UUID message ID for replay detection, a transfer ID for correlation, and an RFC 3339 timestamp. Authentication is supplied by the secure channel and session context rather than embedded secret fields in JSON.
