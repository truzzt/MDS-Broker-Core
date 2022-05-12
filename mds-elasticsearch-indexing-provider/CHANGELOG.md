# Changelog for elasticsearch-indexing-provider
All notable changes to this project will be documented in this file.

## [feature/improveMdsLabelDisplaying] - 11.05.2022
### Added
- Mapping from MDS ontology URIs (e.g. `cat:RoadworksRoadConditions`) to the labels linked with a `rdfs:label` property.
  - This can be used, to map the IDs of an incoming `mds:DataCategory`, `mds:DataSubcategory` or `mds:TransportationMode` to its label.

### Changed
- In ElasticSearch, the fields for the MDS properties don't contain the IDs (i.e. URIs) of the classes like `cat:RoadworksRoadConditions` anymore. This is replaced by the labels annotated to the respective RDF representation of the class.
- For cases where multiple data categories are given, they are split by `,` and then mapped to the respective labels.

## [development] - 11.05.2022
### Changed
- ElasticSearch fields for `publisher`, `maintainer`, `curator` and `sovereign` are replaced by:
  - `publisherAsUri` and `publisherAsObject`
  - `maintainerAsUri` and `maintainerAsObject`
  - `curatorAsUri` and `curatorAsObject`
  - `sovereignAsUri` and `sovereignAsObject`
- This has the advantage that the respected properties can link to either a URI or a complex object (e.g. a `ids:Participant`).

### Known issues
- For `maintainer` and `curator`, there are still `null` objects for the other field (`..AsUri` or `..AsObject`) which is not filled. This is not the case (because it is not necessary) for the other two properties should be fixed in the future.