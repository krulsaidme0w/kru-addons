# Tools upstream decision

Sources inspected on 2026-09-20:

- UI-Utils 2.1.0, `aaac8e3b94fb8dec631b55c1a317307f7a445129`: targets 1.21.1, but declares a hard ModernFix conflict. Repository license/terms include additional redistribution restrictions. Do not copy into a GPL distribution without resolving terms.
- UI-Utils Advanced v2.4.1, `9fa522cd1d46cb0f08e8f6daf58f1984678f1930`: source build targets 1.21.7 despite broad manifest declarations. Retains ModernFix conflict and external Swing fabrication UI.
- DupersUnited `4b61cea0ca90d4a6e27792a2e7a02598f737b150`: 1.21.11, GPL-3.0-or-later. Candidate for component extraction, not whole-mod backport.

UI-Utils/Advanced contain GUI click delay, not a full packet recorder. DupersUnited logger writes selected GUI/inventory/sign summaries to chat; it does not provide the existing KRU-style JSONL capture or generic Cobblemon decoding. Do not advertise those as upstream capabilities. The user explicitly removed file export from requirements; no custom recorder will be built to fill that non-requirement.

## Minimum candidate

DupersUnited components `features/GuiPacketDelayManager.java`, `features/PacketPauseManager.java`, `features/SaveGuiManager.java`, packet summaries in `features/PacketLogger.java`, and item serialization in `events/GuiEvent.java` can be individually adapted. Keep their source SHA and notices. Exclude accounts, proxies, cosmetics and unrelated modules.

Required changes are not cosmetic:

- 1.21.11 packet getters/constructors differ from 1.21.1.
- Meteor controls lifetime. A disabled module must stop interception and input handling.
- Queues and saved GUI references must belong to a connection; disconnect discards them rather than flushing through a new handler.
- Queue release needs a reentry guard and capacity limits.
- Swing/custom screens are replaced with Meteor-themed windows and a narrowly scoped overlay bridge.

Removing a ModernFix `breaks` declaration is not evidence of compatibility. No upstream JAR is installed and no dependency override is applied.

The requested component route is now implemented as behavior-compatible KRU code:
Meteor modules own lifecycle, the packet viewer is live-only, and the old Swing/global
static state is not copied. Upstream attribution and GPL terms are documented in the
tools addon NOTICE. The original UI-Utils license remains relevant to the audit, but
its restricted source is not redistributed verbatim in the KRU JAR.
