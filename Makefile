
MVN=./mvnw
IMAGE_NAME=ehealthid-relying-party
VERSION?=$(shell $(MVN) -q -Dexec.executable=echo -Dexec.args='$${project.version}' --non-recursive exec:exec)
export DOCKER_REPO?=europe-docker.pkg.dev/oviva-pkg/ovi/
GIT_COMMIT=`git rev-parse HEAD`

.PHONY: update-version test unit-test integration-test setup dist build clean install docker

build:
	@$(MVN) -T 8 $(MAVEN_CLI_OPTS) -am package

clean:
	@$(MVN) -T 8 $(MAVEN_CLI_OPTS) -am clean

test:
	@$(MVN) -B verify

update-version:
	@$(MVN) -B versions:set "-DnewVersion=$(VERSION)"

docker: build
	@docker build -t $(IMAGE_NAME):v$(VERSION) .

dist: build
ifndef RELEASE_TAG
	$(error RELEASE_TAG is not set)
endif
	docker buildx build --push --platform linux/amd64,linux/arm64 --label git-commit=$(GIT_COMMIT) --tag "$(DOCKER_REPO)$(IMAGE_NAME):$(RELEASE_TAG)" .
