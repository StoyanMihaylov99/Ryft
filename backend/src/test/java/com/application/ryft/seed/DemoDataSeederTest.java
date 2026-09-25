package com.application.ryft.seed;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.application.ryft.identity.user.dto.UserResponse;
import com.application.ryft.identity.user.service.UserService;
import com.application.ryft.identity.workspace.service.WorkspaceService;
import com.application.ryft.seed.data.DemoDataSet;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

@ExtendWith(MockitoExtension.class)
class DemoDataSeederTest {

    @Mock
    private UserService userService;

    @Mock
    private WorkspaceService workspaceService;

    @Mock
    private DemoDataInstaller installer;

    private DemoDataSeeder seeder;

    @BeforeEach
    void setUp() {
        seeder = new DemoDataSeeder(userService, workspaceService, installer);
    }

    @Test
    void skipsSeedingWhenDemoUserAlreadyRegistered() {
        when(userService.findByEmail(DemoDataSet.DEMO_EMAIL))
                .thenReturn(Optional.of(new UserResponse(UUID.randomUUID(), DemoDataSet.DEMO_EMAIL, "Demo User", null)));

        seeder.run(new DefaultApplicationArguments());

        verify(installer, never()).install();
    }

    @Test
    void installsDemoDataWhenDemoUserNotYetRegistered() {
        when(userService.findByEmail(DemoDataSet.DEMO_EMAIL)).thenReturn(Optional.empty());

        seeder.run(new DefaultApplicationArguments());

        verify(installer, times(1)).install();
    }

    @Test
    void secondRunIsANoOpOnceTheInstallerHasRun() {
        when(userService.findByEmail(DemoDataSet.DEMO_EMAIL))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(new UserResponse(UUID.randomUUID(), DemoDataSet.DEMO_EMAIL, "Demo User", null)));

        seeder.run(new DefaultApplicationArguments());
        seeder.run(new DefaultApplicationArguments());

        verify(installer, times(1)).install();
        verify(userService, times(2)).findByEmail(eq(DemoDataSet.DEMO_EMAIL));
    }

    @Test
    void skipsSeedingWhenAWorkspaceIsAlreadySetUpWithoutTheDemoUser() {
        when(userService.findByEmail(DemoDataSet.DEMO_EMAIL)).thenReturn(Optional.empty());
        when(workspaceService.getCurrentWorkspaceId()).thenReturn(Optional.of(UUID.randomUUID()));

        seeder.run(new DefaultApplicationArguments());

        verify(installer, never()).install();
    }
}
