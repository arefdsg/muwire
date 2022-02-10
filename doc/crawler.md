# Crawling

Crawling the network is one possible method for a HostCache to discover currently active nodes on the network.

### Crawler Ping

The crawler ping is a message sent in a signed datagram to the target ultrapeer.  It contains uncompressed JSON payload with the message type, version and an UUID:

```
{
    type: "CrawlerPing",
    version: 1,
    uuid: "asdf-1234-..."
}
```

### Crawler Pong

A node responds with the following message, also in a signed datagram.  It contains the list of Destinations that the node is currently connected to.  Since the datagram is limited in size, not all Destinations will be able to fit in it, but that should be fine for the purpose of discovery.  The "uuid" field must match that of the CrawlerPing.

```
{
    type: "CrawlerPong",
    version: 1,
    uuid: "asdf-1234-...",
    clientVersion: "MuWire 1.2.3",
	leafSlots: true,  
	peerSlots: true,
    peers: [ b64.1, b64.2...]
}
```

* The `peerSlots` field is set to `true` if the responding node has available connection slots
* The `leafSlots` field is effectively ignored

### Operator control

The operator of the ultrapeer can choose to allow anyone to crawl their node, or just the bundled HostCaches, or nobody at all.
