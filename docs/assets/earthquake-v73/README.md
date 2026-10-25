# Approved icon provenance

PICO clock: official ICON 7.0 component imported into the approved Figma file; key 1ff719da2ccf7e14e9dea4dd502e607b41fe07e7, reviewed instance I27:946;2:72;8750:16393.
Earthquake: approved Earth Observer waveform component.
Figma: https://www.figma.com/design/dcAVPtoJZ6gLaHjxSk57wj/Earth-Observer?node-id=27-941

Downloaded SVGs are retained here. Android VectorDrawable XML is a lossless path/property translation for the existing Kotlin/Compose stack. There are no expiring Figma asset URLs in runtime code. Buttons retain PICO native material and hover. Assets were not redrawn.

Test-only USGS fixture: app/src/test/resources/usgs_quake_week_fixture.geojson, retrieved 2026-10-25 from https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/2.5_week.geojson (406 source records, metadata.generated 1792908682000). It is not packaged as application data. Runtime always uses the repository/cache flow.
