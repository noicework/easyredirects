# Easy Redirects for Magnolia DXP

A powerful and flexible redirect management module for Magnolia DXP, evolved from IBM iX's magkit-vanityurl. This module provides comprehensive URL redirect capabilities with an intuitive admin interface, pattern matching, multi-site support, and seamless integration with Magnolia's content management system.

## Features

### Core Functionality
- **301 & 302 Redirects**: Support for permanent and temporary HTTP redirects
- **Server-side Forwards**: Internal request forwarding without client-side redirects
- **Pattern Matching**: Advanced wildcard and regex pattern support with parameter substitution
- **Multi-site Support**: Site-specific redirects with automatic fallback mechanisms
- **Exclusion Patterns**: Filter out unwanted patterns from redirect processing
- **QR Code Generation**: Built-in QR code generation for easy mobile testing

### Advanced Features
- **Backward Compatibility**: Supports both legacy (`toUri`, `fromUri`) and modern (`targetUrl`, `sourceUrl`) field naming
- **Headless Support**: Dedicated URI mapping for headless CMS implementations
- **Localization**: Full i18n support (currently EN/DE)
- **Public URL Service**: Extensible service layer for custom URL resolution strategies
- **Preview Support**: Test redirects before publishing

## Installation

### Prerequisites
- Magnolia DXP 6.2 or higher
- Java 11 or higher
- Maven 3.6+

### Maven Dependency
Add the following dependency to your Magnolia bundle:

```xml
<dependency>
    <groupId>work.noice</groupId>
    <artifactId>easyredirects</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Module Installation
1. Add the dependency to your project
2. Start Magnolia - the module will be automatically installed
3. Access the Redirects app from the Magnolia AdminCentral

## Configuration

### Basic Setup
The module works out of the box with sensible defaults. Redirects are stored in the `redirects` workspace.

### Virtual URI Mapping
The module automatically registers URI mappings in the following order:
1. `/modules/easyredirects/virtualUriMappings/default` - Standard redirects
2. `/modules/easyredirects/virtualUriMappings/headless` - Headless-specific redirects

### Public URL Service Configuration
Configure the public URL service in your site definition:

```yaml
/modules/multisite/config/sites/[your-site]:
  publicUrlService:
    class: work.noice.easyredirects.service.EasyRedirectsPublicUrlService
```

## Usage

### Creating Redirects

1. Open the **Redirects** app in Magnolia AdminCentral
2. Click **Add redirect**
3. Fill in the required fields:
   - **Source URL**: The URL pattern to redirect from (supports wildcards)
   - **Target URL**: The destination URL
   - **Type**: Choose between 301 (permanent) or 302 (temporary)
   - **Site**: Select target site (optional, defaults to all sites)
   - **Active**: Enable/disable the redirect

### Pattern Matching

The module supports advanced pattern matching:

#### Wildcard Patterns
```
/old-products/*  →  /products/*
/blog/*/comments  →  /blog/*/discussion
```

#### Regex Patterns
```
/product-(\d+)  →  /products/$1
/category/(.*)/page-(\d+)  →  /categories/$1?page=$2
```

### Exclusion Patterns

Define patterns to exclude from redirect processing in the dialog:
- `.*/\.resources/.*` - Exclude resource URLs
- `.*\.(jpg|png|gif|css|js)$` - Exclude static assets

### Testing Redirects

1. Use the **Preview** action in the Redirects app
2. The preview popup shows:
   - Redirect type and status
   - QR code for mobile testing
   - Direct link to test the redirect

## API Usage

### Programmatic Redirect Creation

```java
@Inject
private RedirectService redirectService;

public void createRedirect() {
    Redirect redirect = new Redirect();
    redirect.setSourceUrl("/old-path");
    redirect.setTargetUrl("/new-path");
    redirect.setType(RedirectType.PERMANENT_301);
    redirect.setSite("my-site");
    
    redirectService.save(redirect);
}
```

### Custom Public URL Service

Extend the base service for custom URL resolution:

```java
public class CustomPublicUrlService extends SimplePublicUrlService {
    @Override
    public String createTargetUrl(Node node, String path) {
        // Custom URL generation logic
        return super.createTargetUrl(node, path);
    }
}
```

## Architecture

### Components

- **RedirectsUriMapping**: Core virtual URI mapping implementation
- **RedirectService**: Business logic for redirect management
- **SimplePublicUrlService**: Default implementation of public URL service
- **RedirectsHeadlessUriMapping**: Specialized mapping for headless setups
- **Redirect**: Core domain model
- **RedirectRepository**: Data access layer

### Extension Points

- Custom `PublicUrlService` implementations
- Additional virtual URI mappings
- Custom redirect validators
- Alternative storage backends

## Development

### Building from Source

```bash
git clone https://github.com/noicework/easyredirects.git
cd easyredirects
mvn clean install
```

### Running Tests

```bash
mvn test
```

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Credits

Based on the original magkit-vanityurl module by IBM iX. Enhanced and maintained by the Magnolia community.

## Support

For issues, questions, or contributions, please use the GitHub issue tracker.