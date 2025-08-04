# Easy Redirects - Content Author's Guide

## What are URL Redirects?

URL redirects are like mail forwarding for websites. When someone tries to visit an old web address, a redirect automatically sends them to the new location. This ensures visitors and search engines can always find your content, even when pages move or URLs change.

## Why Use Redirects?

- **Prevent broken links** when you move or rename pages
- **Guide visitors** from old campaign URLs to current promotions
- **Maintain SEO value** by telling search engines where content has moved
- **Create memorable shortcuts** for marketing campaigns

## Getting Started

### Accessing the Redirects App

1. Log into Magnolia AdminCentral
2. Look for the **Redirects** app in your app launcher
3. Click to open the redirect management interface

### Understanding the Interface

The Redirects app shows a list of all configured redirects with:
- **From URL**: The old address visitors might use
- **To URL**: Where they should be sent instead
- **Site**: Which website the redirect applies to
- **Status**: Whether the redirect is active or inactive

## Creating Your First Redirect

### Step 1: Add a New Redirect

Click the **"Add redirect"** button in the action bar.

### Step 2: Fill in the Basic Information

#### From URL (Source)
This is the old URL that visitors might try to access.
- Must start with a forward slash (/)
- Don't include the domain name
- **Examples:**
  - `/old-products`
  - `/2023-summer-sale`
  - `/about-us.html`

#### To URL (Target)
This is where visitors should be sent.
- Can be a full URL with domain: `https://www.example.com/new-products`
- Or a relative path: `/products/new-arrivals`
- **Examples:**
  - `/new-products`
  - `https://www.example.com/current-promotions`
  - `/about/company-info`

#### Redirect Type
Choose the appropriate redirect type:

**301 - Permanent Redirect**
- Use when content has permanently moved
- Search engines will update their records
- Best for: Site restructuring, permanent URL changes

**302 - Temporary Redirect**
- Use for temporary changes
- Search engines keep the original URL in their index
- Best for: Seasonal campaigns, maintenance, A/B testing

#### Site Selection
If your Magnolia instance manages multiple websites:
- Select the specific site for this redirect
- Choose "fallback" to apply to all sites
- Leave empty for the default site

#### Description (Optional)
Add notes about why this redirect exists:
- "Old product line - redirected to new catalog"
- "2023 Christmas campaign - expires January"
- "Department renamed from Services to Solutions"

### Step 3: Save and Activate

1. Click **Save** to create the redirect
2. The redirect is automatically active once saved
3. Test it immediately by visiting the "From URL"

## Common Redirect Scenarios

### Scenario 1: Page Reorganization
You're moving all blog posts from `/news/` to `/blog/`:
- From URL: `/news/company-update-2024`
- To URL: `/blog/company-update-2024`
- Type: 301 Permanent

### Scenario 2: Seasonal Campaign
Redirecting a marketing campaign URL:
- From URL: `/summer2024`
- To URL: `/promotions/summer-collection`
- Type: 302 Temporary

### Scenario 3: Product Retirement
Old product page to category page:
- From URL: `/products/widget-pro-v1`
- To URL: `/products/widgets`
- Type: 301 Permanent

### Scenario 4: Fixing Common Typos
Catching common misspellings:
- From URL: `/contcat-us`
- To URL: `/contact-us`
- Type: 301 Permanent

## Advanced Features

### Using Wildcards (Pattern Matching)

Wildcards let you redirect multiple URLs with one rule:

**Simple Wildcard (*)**
Matches any characters:
- From: `/old-products/*`
- To: `/products/*`
- Result: `/old-products/shoes` → `/products/shoes`

**Multiple Wildcards**
You can use numbered placeholders:
- From: `/archive/*/posts/*`
- To: `/blog/$1/$2`
- Result: `/archive/2023/posts/summer` → `/blog/2023/summer`

### URL Parameters

Preserve or add URL parameters:
- From: `/search`
- To: `/products/search`
- Visitor goes to: `/search?q=shoes`
- They arrive at: `/products/search?q=shoes`

## Testing Your Redirects

### Before Publishing
1. Save your redirect
2. Open a new browser tab (or incognito window)
3. Type in your "From URL"
4. Verify you arrive at the correct "To URL"

### Common Testing Tips
- Clear your browser cache if redirects seem stuck
- Test with the full URL including domain
- Try variations users might type
- Check both with and without trailing slashes

## Best Practices

### DO:
- **Keep redirects simple** - Direct paths are better than chains
- **Document with descriptions** - Future editors will thank you
- **Test immediately** - Verify redirects work as expected
- **Clean up old redirects** - Remove temporary redirects when campaigns end
- **Use permanent redirects carefully** - They're hard to undo

### DON'T:
- **Create redirect chains** - Avoid A→B→C situations
- **Redirect to redirects** - Always point to final destinations
- **Use temporary for permanent moves** - This confuses search engines
- **Forget the forward slash** - URLs must start with /
- **Include URL parameters in From URL** - Keep source URLs clean

## Troubleshooting

### Redirect Not Working
1. **Check the From URL** - Must start with /
2. **Verify activation** - Ensure the redirect is published
3. **Clear browser cache** - Old redirects might be cached
4. **Check site selection** - Ensure correct site is selected

### Error Messages

**"From URL is not unique"**
- This URL already has a redirect
- Edit the existing redirect instead
- Or choose a different From URL

**"From URL must start with a slash"**
- Add / at the beginning
- Correct: `/old-page`
- Incorrect: `old-page` or `www.site.com/old-page`

### Getting Help

If you encounter issues:
1. Check if a redirect already exists for your URL
2. Verify your URLs are formatted correctly
3. Ask your site administrator about site-specific settings
4. Contact your Magnolia support team for complex patterns

## Quick Reference

### Redirect Types
- **301**: Permanent move (use for most redirects)
- **302**: Temporary move (use for campaigns)

### URL Format Examples
- ✅ `/products`
- ✅ `/products/shoes`
- ✅ `/info.html`
- ❌ `products` (missing /)
- ❌ `http://site.com/products` (don't include domain in From URL)

### Wildcard Examples
- `/old/*` → `/new/*` (simple wildcard)
- `/blog/*/article/*` → `/articles/$1/$2` (multiple captures)

Remember: When in doubt, start simple. You can always create more specific redirects as needed!