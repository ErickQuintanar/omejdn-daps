FROM ubuntu

# Copy testing folder and required keys for testing
COPY tests/ tests/
COPY keys/ keys/

# Get required dependencies to install sdkman
RUN apt-get update
RUN rm /bin/sh && ln -s /bin/bash /bin/sh
RUN apt-get -qq -y install bash curl unzip zip

# Install sdkman
RUN curl -s "https://get.sdkman.io" | bash
RUN bash -c "source $HOME/.sdkman/bin/sdkman-init.sh"

# Install required versions of java, maven and kotlin
RUN bash -c "source $HOME/.sdkman/bin/sdkman-init.sh && \
yes | sdk install java 11.0.10-open && \
yes | sdk install maven 3.8.1 && \
yes | sdk install kotlin 1.4.31"

# Pre-install java and kotlin libraries required
RUN bash -c "source $HOME/.sdkman/bin/sdkman-init.sh && cd tests && mvn package -Dmaven.test.skip"

# Set reference for sdkman everytime we execute a command
ENTRYPOINT bash -c "source $HOME/.sdkman/bin/sdkman-init.sh && $0 $@"

# Run tests
CMD ["cd tests && mvn clean test"]
