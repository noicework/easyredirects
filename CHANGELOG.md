# Changelog

All notable changes to this project will be documented in this file.

## [2.0.0] - 2025-01-09

### Breaking Changes
- **Magnolia 6.4+ Required**: This version requires Magnolia DXP 6.4.1 or higher
- **Java 17 Required**: Minimum Java version is now Java 17
- **Jakarta EE 10**: Migrated from `javax.inject` to `jakarta.inject` for Tomcat 10+ compatibility
- **New App Configuration**: Complete rewrite of the app descriptor to use Magnolia 6.4's new UI framework

### Changed
- Updated Magnolia dependencies to 6.4.1
- Updated magnolia-content-types to 3.0.0
- Updated magnolia-site to 3.0.0
- Updated servlet-api to Jakarta Servlet API 6.0.0
- Updated JSP API to Jakarta JSP API 3.1.0
- Rewrote app descriptor using explicit class definitions (ContentAppDescriptor, BrowserDescriptor, DetailDescriptor)
- Updated content type to use new field names (fromUrl, toUrl, redirectType, etc.)
- Simplified form fields for compatibility with Magnolia's New UI Forms

### Fixed
- Form rendering issues with Magnolia 6.4's React-based frontend
- radioButtonGroupField now uses proper optionListDatasource format

### Notes
- For Magnolia 6.2.x users, please use version 1.2.0
- The `javax.jcr` imports remain unchanged as Jackrabbit JCR still uses the javax namespace

## [1.2.0] - Previous Release

### Features
- URL redirects with 301 and 302 support
- Server-side forwards
- Pattern matching with wildcards and regex
- Multi-site support with fallback mechanisms
- QR code generation for mobile testing
- Headless CMS support
- Backward compatibility with legacy field names
