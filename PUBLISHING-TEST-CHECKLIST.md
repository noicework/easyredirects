# Publishing and readable redirect names (2.1.1)

These changes are in EasyRedirects itself, not a site-specific app decoration.
Deploy the candidate through the normal non-production backend build before UI
acceptance. Do not copy a JAR into a running production pod or publish the real
governance redirect as part of this test.

## Automated checks

```sh
mvn test -Dgpg.skip=true
mvn test -Dmagnolia.version=6.4.6 -Dgpg.skip=true
mvn package -Dgpg.skip=true
```

Do not run `mvn deploy` for a test build: this project's publishing plugin is
configured to publish releases automatically. The final release version is `2.1.1`.
The `2.1.1-rc1` candidate passed VCC dev acceptance as reported by the tester.
The final package must still be smoke-tested on dev before production promotion.

## Author and public acceptance checks

Use a disposable path such as `/redirect-qa-20260921`, an existing destination,
and the actual site name. Test with an administrator and a normal redirect editor.

Before the UI checks, inspect Magnolia's module upgrade screen and startup logs.
The candidate also fixes the existing Maven descriptor filtering omission, so
Magnolia now receives the real module name/version rather than `${project.*}`
placeholders. Confirm it recognizes `easyredirects` as the existing module, upgrades
it successfully, and preserves its configuration and `redirects` workspace. If it
unexpectedly proposes a first-time installation, review the existing module
registration before proceeding, particularly the legacy cleanup install tasks.

1. Open Redirects. Confirm the Publication status column and Publish action appear.
2. Create and save the test redirect. Confirm the internal name is
   `redirect-qa-20260921`, its From/To URLs are unchanged, and neither public
   instance has received it yet.
3. Publish from Redirects. Confirm success, published status, and the expected
   status/Location header on **both** public instances using the correct Host.
4. Edit its target and save. Confirm the internal name/UUID stay unchanged and
   public targets remain unchanged until Publish is clicked again.
5. Unpublish. Confirm both publics stop redirecting, while the author record remains.
6. Publish again, then Delete. Confirm the marked-deleted selection offers
   Delete permanently and (where version history exists) Restore. Test Restore,
   then repeat deletion and Delete permanently; verify removal on both publics.
7. Create two distinct From URLs with the same normalized name, e.g.
   `/redirect-qa/a-b` and `/redirect-qa-a/b`. Confirm unique internal names and
   independent editable records, with no overwrite.
8. Create a redirect inside a new folder. Publish the folder first, then the
   redirect. Verify the same publish/unpublish lifecycle.
9. Edit an existing `untitledX` record and cancel; confirm its name is unchanged.
   On a disposable existing record, save an edit and verify its name/UUID remain unchanged.
10. Try saving with required fields missing. Confirm validation prevents the save.
11. Confirm an editor without repository write access cannot publish, unpublish,
    or delete. Existing app/workspace ACLs and receiver configuration are unchanged.
12. Clean up only the disposable test records, publishing any deletions as required.

## Local browser results (21 September 2026)

Tested on the existing local Magnolia 6.4.6 author, not a public environment.

- Publication status and Publish/Unpublish actions render; Unpublish is disabled
  for a draft.
- New redirects receive readable names. Distinct URLs that normalize to the same
  name receive unique suffixes, with no overwrite.
- Editing a redirect's From URL preserves its existing internal name and UUID.
- Draft deletion works. All disposable records created for these checks were removed.
- Publish invokes Magnolia's command, but transfer cannot complete because the
  local runtime reports no publishing receivers. Successful publish/unpublish,
  published-record restore/permanent deletion, and editor permissions still need
  the author/public acceptance checks above. A version or status label alone is
  not proof of a successful transfer.

The browser test found that Magnolia's new UI form bypassed the custom Java save
action. This app now explicitly uses the supported `vaadinForm` editor, keeping
readable naming active without changing other apps. See
[Magnolia's new UI forms documentation](https://docs.magnolia-cms.com/product-docs/developing/templating/dialog-definition/new-ui-forms/).
The browser checks used a local hot-reloaded copy of the candidate app YAML with
the candidate Java code loaded in the existing author. The packaged JAR must still
pass the normal dev deployment checks.

## Release scope

- No bulk rename or content migration.
- No changes to redirect matching, site selection, receivers, ingress, or frontend.
- Save is not Publish. Existing records can be published directly from Redirects.
- A JAR rollback only reverts the code/UI; it does not undo content that a tester
  explicitly published or deleted. Use disposable records and complete cleanup.

The publish action uses Magnolia's `versioned/publish` command. Unpublish uses the
default catalog because Magnolia 6.4 removed `versioned/unpublish`:
[Magnolia publishing module](https://docs.magnolia-cms.com/product-docs/modules/list-of-modules/publishing-module/).
