package com.example.localizacion;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AnticipateOvershootInterpolator;

import com.example.localizacion.databinding.FragmentSplashBinding;

public class SplashFragment extends Fragment {
    private FragmentSplashBinding binding;

    public SplashFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentSplashBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Iniciar las animaciones
        startAnimations();

        // Al pulsar continuar, quitamos el fragmento (estilo splash)
        binding.button.setOnClickListener(v -> getParentFragmentManager().beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                .remove(SplashFragment.this)
                .commit());
    }

    private void startAnimations() {
        if (binding == null) return;

        // 1. Animación del Logo (Escalado con rebote)
        binding.imgLogo.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(800)
                .setInterpolator(new AnticipateOvershootInterpolator())
                .start();

        // 2. Animación del Título
        binding.txtAppNameSplash.animate()
                .alpha(1f)
                .setDuration(600)
                .setStartDelay(400)
                .start();

        // 3. Animación del Autor
        binding.txtAutorSplash.animate()
                .alpha(1f)
                .setDuration(600)
                .setStartDelay(600)
                .start();

        // 4. Animación del ProgressBar (Aparece con fade)
        binding.progressSplash.animate()
                .alpha(1f)
                .setDuration(800)
                .setStartDelay(800)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();

        // 5. Animación del Card de Disclaimer (Añadida para corregir visibilidad)
        binding.disclaimerCard.animate()
                .alpha(1f)
                .translationY(0)
                .setDuration(700)
                .setStartDelay(1000)
                .start();

        // 6. Animación del Botón (Aparece al final)
        binding.button.animate()
                .alpha(1f)
                .setDuration(500)
                .setStartDelay(1400)
                .start();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}