# Changelog for elasticsearch-indexing-provider
All notable changes to this project will be documented in this file.

## [feature/fixUriOrModelClassHandling] - 11.05.2022
### Changed
- ElasticSearch fields for `publisher`, `maintainer`, `curator` and `sovereign` are replaced by:
  - `publisherAsUri` and `publisherAsObject`
  - `maintainerAsUri` and `maintainerAsObject`
  - `curatorAsUri` and `curatorAsObject`
  - `sovereignAsUri` and `sovereignAsObject`
- This has the advantage that the respected properties can link to either a URI or a complex object (e.g. a `ids:Participant`).

### Known issues
- For `maintainer` and `curator`, there are still `null` objects for the other field (`..AsUri` or `..AsObject`) which is not filled. This is not the case (because it is not necessary) for the other two properties should be fixed in the future.