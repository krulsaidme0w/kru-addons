# PC Organizer: Collection mode

Collection is the default layout mode, separate from UI Utils. Open a regular
Cobblemon PC, choose **Preview**, inspect group tabs and destination boxes, then
**Apply preview**. Preview sends no mutations; hover a destination box to see
its exact planned slots. The group tabs show the plan before application, not
an already-applied server layout.

## Roles and automatic groups

The first six role anchors are MAIN, SPECIAL, SHINIES, UTILITY, TRADE, BREEDING.
Existing named role boxes move as complete contents/slot layouts to their
previewed positions. MAIN and UTILITY contents remain untouched. TRADE and
BREEDING are routing boundaries: their contents are sorted in-place within the
same role's boxes (species, form, total IV descending, then UUID), never routed
to another category. Explicitly locked box numbers cannot move, be renamed or
be sorted, including during the role reorder. A conflicting lock blocks the
preview rather than being ignored.

Among unprotected Pokemon, shiny takes priority, then intrinsic SPECIAL class
(legendary, mythical, ultra beast, paradox, restricted), then primary type.
Spawn rarity is not used for SPECIAL. The reader uses live species labels;
unavailable class or shiny facts block automatic classification instead of
turning unknown Pokemon into ordinary ones. Unknown type has its own
UNCLASSIFIED group.

SHINIES and SPECIAL each retain their anchor and receive additional boxes after
box 6 when needed. Type groups likewise receive enough existing boxes, with
names such as WATER 1/2 and WATER 2/2. No server storage capacity is created.
Insufficient boxes block before changes. Existing group boxes are reused to the
extent needed before allocating new ones; unused surplus returns to SPARE.
Groups may span nonadjacent boxes to avoid moving unrelated groups.

Within each automatic group and within unpinned TRADE/BREEDING zones: species,
form, total IV descending, then UUID for stable ties. Dual-type Pokemon
physically belong to their primary-type group. No automatic selection of
breeding or trade candidates is performed.

## Applying and navigation

Apply executes the exact preview: role reorder, Pokemon moves/swaps, generated
names. Every request waits for an exact server snapshot acknowledgement. Moves
are complete before generated names are sent. Unexpected changes stop the run;
reopen or preview the remaining live layout instead of assuming rollback.
Stopping leaves acknowledged changes in place; an in-flight request may finish.

After completion the group tabs navigate the real PC locally. Clicking the same
group cycles through its boxes. They do not create a second inventory or send
transfers. During preview they only select the group's destination details.

Manual mode retains scope, filters and sort settings from the previous organizer;
it does not allocate collection groups. Collection always plans the whole PC,
with manual filters inapplicable. No party changes or releases are supported.

## Verification boundary

Pure allocation, reader classification, grouping, frozen preview execution and
name acknowledgement have offline tests. Native StorageWidget.setBox(int) was
inspected against installed Cobblemon 1.8.1. No game installation or live server
sorting is performed by this development task.
