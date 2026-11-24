# Better Red: build the release APK in a container (docker or podman). See docs/releasing.md.
#
#   make apk                                          unsigned APK in dist/
#   make apk KEYSTORE=~/keys/better-red.jks KEY_ALIAS=better-red
#                                                     signed (asks for the password)

ENGINE    ?= $(shell command -v podman 2>/dev/null || command -v docker 2>/dev/null)
DIST      ?= dist
KEYSTORE  ?=
KEY_ALIAS ?= better-red

help:	## Show all Makefile targets.
	@echo "Better Red: Makefile targets"
	@echo ""
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' Makefile | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[33m%-30s\033[0m %s\n", $$1, $$2}'

version: ## App version (versionName in app/build.gradle.kts)
	@sed -n 's/^ *versionName = "\(.*\)".*/\1/p' app/build.gradle.kts

apk: ## Build the release APK in a container into dist/ (KEYSTORE=... to sign)
	@test -n "$(ENGINE)" || (echo "Needs podman or docker" && exit 1)
	@rm -rf $(DIST)
ifeq ($(KEYSTORE),)
	$(ENGINE) build --target apk --output type=local,dest=$(DIST) .
else
	@test -f "$(KEYSTORE)" || (echo "No keystore at $(KEYSTORE)" && exit 1)
	@if [ -z "$$BR_KEYSTORE_PASSWORD" ]; then printf "Keystore password: "; stty -echo; read pw; stty echo; echo; \
	  export BR_KEYSTORE_PASSWORD="$$pw"; fi; \
	BR_KEY_ALIAS="$(KEY_ALIAS)" BR_KEY_PASSWORD="$${BR_KEY_PASSWORD:-$$BR_KEYSTORE_PASSWORD}" \
	$(ENGINE) build --target apk --output type=local,dest=$(DIST) \
	  --secret id=keystore,src="$(KEYSTORE)" \
	  --secret id=keystore_password,env=BR_KEYSTORE_PASSWORD \
	  --secret id=key_alias,env=BR_KEY_ALIAS \
	  --secret id=key_password,env=BR_KEY_PASSWORD .
endif
	@ls -l $(DIST)

clean: ## Remove dist/
	rm -rf $(DIST)

.PHONY: help version apk clean
