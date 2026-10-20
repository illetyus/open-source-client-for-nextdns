# Offline traffic map data

The analytics destination map uses a local, simplified copy of Natural Earth's
1:110m Cultural Vectors, Admin 0 – Countries geometry. Natural Earth publishes
the dataset in the public domain. The source and terms are linked from the
in-app third-party notices at
app/src/main/assets/licenses/world-map-NOTICE.txt.

The bundled maps/world-countries.tsv stores ISO 3166-1 alpha-2 country codes
and simplified rings. Coordinates are normalized equirectangular longitude and
latitude values scaled to integers. The current asset contains 175 ISO-coded
country records, 285 polygons, and 3,711 coordinate points. Territories without
an ISO alpha-2 code in the source are omitted because the analytics API keys
country data with two-letter codes.

Asset SHA-256: D100699CD75B33D8DB54D97655D876F850A07BF25140DD1F99AE687E1A8B85FF.

The map is rendered from that asset without map tiles, network requests, or
location permission. Selecting a country displays the query count, percentage
of total queries, and up to five domain samples supplied by the analytics
response. This visualization communicates the API's country-level attribution;
it does not identify an individual user's physical location.
